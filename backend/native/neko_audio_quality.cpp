#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cerrno>
#include <fcntl.h>
#include <fstream>
#include <string>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

namespace {

enum Tier { UNKNOWN = 0, STANDARD = 1, HQ = 2, SQ = 3, HIRES = 4 };

uint32_t readLe32(const unsigned char *p) {
    return uint32_t(p[0]) | (uint32_t(p[1]) << 8) | (uint32_t(p[2]) << 16) | (uint32_t(p[3]) << 24);
}

uint32_t readBe32(const unsigned char *p) {
    return (uint32_t(p[0]) << 24) | (uint32_t(p[1]) << 16) | (uint32_t(p[2]) << 8) | uint32_t(p[3]);
}

int tierFromBitrate(int bps) {
    if (bps >= 2000000) return HIRES;
    if (bps >= 441000) return SQ;
    if (bps >= 320000) return HQ;
    if (bps > 0) return STANDARD;
    return UNKNOWN;
}

bool parseFlac(const std::vector<unsigned char> &data, long *out) {
    if (data.size() < 42 || std::string(data.begin(), data.begin() + 4) != "fLaC") return false;
    uint32_t header = readBe32(data.data() + 4);
    int blockType = int((header >> 24) & 0x7f);
    uint32_t blockSize = header & 0x00ffffffu;
    if (blockType != 0 || blockSize < 18 || 8 + blockSize > data.size()) return false;
    const unsigned char *p = data.data() + 8;
    int sampleRate = int((p[10] << 12) | (p[11] << 4) | (p[12] >> 4));
    int bits = int((((p[12] & 1) << 4) | ((p[13] & 0xf0) >> 4)) + 1);
    int channels = int((p[12] & 0x0e) >> 1) + 1;
    if (sampleRate <= 0) return false;
    int tier = (sampleRate >= 96000 || bits >= 24) ? HIRES : SQ;
    out[0] = tier;
    out[1] = int(std::min<int64_t>(sampleRate * std::max(16, bits) * channels, 0x7fffffff));
    out[2] = sampleRate;
    out[3] = bits;
    out[4] = channels;
    return true;
}

bool parseWav(const std::vector<unsigned char> &data, long *out) {
    if (data.size() < 44 || std::string(data.begin(), data.begin() + 4) != "RIFF" ||
        std::string(data.begin() + 8, data.begin() + 12) != "WAVE") return false;
    for (size_t off = 12; off + 8 <= data.size();) {
        const unsigned char *p = data.data() + off;
        uint32_t size = readLe32(p + 4);
        if (std::string(p, p + 4) == "fmt " && size >= 16 && off + 8 + size <= data.size()) {
            const unsigned char *fmt = p + 8;
            int sampleRate = int(readLe32(fmt + 4));
            int byteRate = int(readLe32(fmt + 8));
            int channels = int(fmt[2] | (fmt[3] << 8));
            int bits = int(fmt[14] | (fmt[15] << 8));
            out[0] = (sampleRate >= 96000 || bits >= 24) ? HIRES :
                     (byteRate * 8 >= 1411200 ? SQ : tierFromBitrate(byteRate * 8));
            out[1] = byteRate * 8;
            out[2] = sampleRate;
            out[3] = bits;
            out[4] = channels;
            return sampleRate > 0;
        }
        off += 8 + size + (size & 1);
    }
    return false;
}

bool parseMp3(const std::vector<unsigned char> &data, long *out) {
    static const int table[] = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    for (size_t i = 0; i + 4 < data.size(); ++i) {
        if (data[i] != 0xff || (data[i + 1] & 0xe0) != 0xe0) continue;
        int kbps = table[(data[i + 2] >> 4) & 0x0f];
        if (kbps <= 0) continue;
        out[0] = tierFromBitrate(kbps * 1000);
        out[1] = kbps * 1000;
        out[2] = 0;
        out[3] = 0;
        out[4] = 2;
        return true;
    }
    return false;
}

bool readHead(const std::string &path, std::vector<unsigned char> *data) {
    std::ifstream input(path, std::ios::binary);
    if (!input) return false;
    data->resize(256 * 1024);
    input.read(reinterpret_cast<char *>(data->data()), data->size());
    data->resize(static_cast<size_t>(input.gcount()));
    return !data->empty();
}

int runFfmpeg(const std::string &ffmpeg, const std::string &source, const std::string &target, int kbps) {
    pid_t pid = fork();
    if (pid < 0) return -1;
    if (pid == 0) {
        if (kbps == 0) {
            execlp(ffmpeg.c_str(), ffmpeg.c_str(), "-hide_banner", "-loglevel", "error", "-y", "-threads", "0",
                   "-i", source.c_str(), "-vn", "-map_metadata", "-1", "-codec:a", "flac",
                   "-ar", "44100", "-sample_fmt", "s16", "-ac", "2", "-f", "flac",
                   target.c_str(), nullptr);
        } else {
            execlp(ffmpeg.c_str(), ffmpeg.c_str(), "-hide_banner", "-loglevel", "error", "-y", "-threads", "0",
                   "-i", source.c_str(), "-vn", "-map_metadata", "-1", "-codec:a", "libmp3lame",
                   "-b:a", (std::to_string(kbps) + "k").c_str(), "-ar", "44100", "-ac", "2",
                   "-f", "mp3", target.c_str(), nullptr);
        }
        _exit(127);
    }
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) return -1;
    }
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128;
}

} // namespace

extern "C" JNIEXPORT jlongArray JNICALL Java_com_neko_music_nativeaudio_NativeAudioQuality_probe
  (JNIEnv *env, jclass, jstring path) {
    const char *raw = env->GetStringUTFChars(path, nullptr);
    std::vector<unsigned char> data;
    long values[5] = {-1, 0, 0, 0, 0};
    bool ok = readHead(raw, &data) && (parseFlac(data, values) || parseWav(data, values) || parseMp3(data, values));
    env->ReleaseStringUTFChars(path, raw);
    if (!ok) return nullptr;
    jlong result[5] = {values[0], values[1], values[2], values[3], values[4]};
    jlongArray array = env->NewLongArray(5);
    env->SetLongArrayRegion(array, 0, 5, result);
    return array;
}

extern "C" JNIEXPORT jint JNICALL Java_com_neko_music_nativeaudio_NativeAudioQuality_transcode
  (JNIEnv *env, jclass, jstring ffmpeg, jstring source, jstring target, jint kbps) {
    const char *ffmpegRaw = env->GetStringUTFChars(ffmpeg, nullptr);
    const char *sourceRaw = env->GetStringUTFChars(source, nullptr);
    const char *targetRaw = env->GetStringUTFChars(target, nullptr);
    int result = runFfmpeg(ffmpegRaw, sourceRaw, targetRaw, kbps);
    env->ReleaseStringUTFChars(ffmpeg, ffmpegRaw);
    env->ReleaseStringUTFChars(source, sourceRaw);
    env->ReleaseStringUTFChars(target, targetRaw);
    return result;
}
