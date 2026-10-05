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

int64_t fileSizeBytes(const std::string &path) {
    std::ifstream input(path, std::ios::binary | std::ios::ate);
    if (!input) return 0;
    std::streamoff size = input.tellg();
    return size > 0 ? static_cast<int64_t>(size) : 0;
}

/** 返回 ID3v2 标签占用的字节数（不含标签时返回 0），用于跳过标签直接扫描音频帧。 */
int64_t id3v2TagSize(const std::string &path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) return 0;
    unsigned char header[10] = {0};
    input.read(reinterpret_cast<char *>(header), 10);
    if (input.gcount() < 10) return 0;
    if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return 0;
    if (header[3] == 0xff || header[4] == 0xff) return 0;
    int64_t size = (static_cast<int64_t>(header[6] & 0x7f) << 21) |
                   (static_cast<int64_t>(header[7] & 0x7f) << 14) |
                   (static_cast<int64_t>(header[8] & 0x7f) << 7) |
                   static_cast<int64_t>(header[9] & 0x7f);
    size += 10;
    if (header[5] & 0x10) size += 10;
    return size;
}

/** 遍历 FLAC 元数据块头，得到纯音频流字节数（排除内嵌封面、标签等）。 */
int64_t flacAudioBytes(const std::string &path, int64_t fileSize) {
    if (fileSize <= 0) return 0;
    std::ifstream input(path, std::ios::binary);
    if (!input) return fileSize;
    unsigned char header[4] = {0};
    input.read(reinterpret_cast<char *>(header), 4);
    if (input.gcount() < 4) return fileSize;
    int64_t offset = 4;
    while (true) {
        input.read(reinterpret_cast<char *>(header), 4);
        if (input.gcount() < 4) return fileSize;
        offset += 4;
        bool last = (header[0] & 0x80) != 0;
        offset += (static_cast<int64_t>(header[1]) << 16) |
                  (static_cast<int64_t>(header[2]) << 8) | static_cast<int64_t>(header[3]);
        if (last) break;
        if (offset >= fileSize) return 0;
        input.seekg(offset);
        if (!input) return fileSize;
    }
    int64_t audioBytes = fileSize - offset;
    return audioBytes > 0 ? audioBytes : fileSize;
}

bool parseFlac(const std::string &path, const std::vector<unsigned char> &data, int64_t fileSize, long *out) {
    if (data.size() < 42 || std::string(data.begin(), data.begin() + 4) != "fLaC") return false;
    uint32_t header = readBe32(data.data() + 4);
    int blockType = int((header >> 24) & 0x7f);
    uint32_t blockSize = header & 0x00ffffffu;
    if (blockType != 0 || blockSize < 18 || 8 + blockSize > data.size()) return false;
    const unsigned char *p = data.data() + 8;
    int sampleRate = int((p[10] << 12) | (p[11] << 4) | (p[12] >> 4));
    int bits = int((((p[12] & 1) << 4) | ((p[13] & 0xf0) >> 4)) + 1);
    int channels = int((p[12] & 0x0e) >> 1) + 1;
    uint64_t totalSamples = (static_cast<uint64_t>(p[13] & 0x0f) << 32) |
                            (static_cast<uint64_t>(p[14]) << 24) |
                            (static_cast<uint64_t>(p[15]) << 16) |
                            (static_cast<uint64_t>(p[16]) << 8) |
                            static_cast<uint64_t>(p[17]);
    if (sampleRate <= 0) return false;
    int tier = (sampleRate >= 96000 || bits >= 24) ? HIRES : SQ;
    int64_t pcmBps = static_cast<int64_t>(sampleRate) * std::max(16, bits) * channels;
    int64_t audioBytes = flacAudioBytes(path, fileSize);
    int64_t realBps = (totalSamples > 0 && audioBytes > 0)
            ? (audioBytes * 8LL * sampleRate) / static_cast<int64_t>(totalSamples)
            : pcmBps;
    out[0] = tier;
    out[1] = int(std::min<int64_t>(realBps, 0x7fffffff));
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

struct Mp3Header {
    int versionBits = 0;
    int layerBits = 0;
    int bitrateBps = 0;
    int sampleRate = 0;
    int channels = 2;
    int samplesPerFrame = 1152;
    int frameLength = 0;
};

/** 解析单个 MP3 帧头并校验字段合法性；frameLength 用于连续帧校验。 */
bool parseMp3Header(const unsigned char *p, Mp3Header *h) {
    static const int v1l1[]  = {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0};
    static const int v1l2[]  = {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0};
    static const int v1l3[]  = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    static const int v2l1[]  = {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0};
    static const int v2l23[] = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
    static const int sr1[]   = {44100, 48000, 32000};
    static const int sr2[]   = {22050, 24000, 16000};
    static const int sr25[]  = {11025, 12000, 8000};

    if (p[0] != 0xff || (p[1] & 0xe0) != 0xe0) return false;
    int versionBits = (p[1] >> 3) & 0x03;
    int layerBits = (p[1] >> 1) & 0x03;
    int bitrateIndex = (p[2] >> 4) & 0x0f;
    int sampleRateIndex = (p[2] >> 2) & 0x03;
    int padding = (p[2] >> 1) & 0x01;
    if (versionBits == 1 || layerBits == 0) return false;
    if (bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) return false;
    bool mpeg1 = versionBits == 3;
    const int *table = mpeg1
            ? (layerBits == 3 ? v1l1 : (layerBits == 2 ? v1l2 : v1l3))
            : (layerBits == 3 ? v2l1 : v2l23);
    int kbps = table[bitrateIndex];
    if (kbps <= 0) return false;

    h->versionBits = versionBits;
    h->layerBits = layerBits;
    h->bitrateBps = kbps * 1000;
    h->sampleRate = versionBits == 3 ? sr1[sampleRateIndex]
                  : (versionBits == 2 ? sr2[sampleRateIndex] : sr25[sampleRateIndex]);
    h->channels = ((p[3] >> 6) & 0x03) == 3 ? 1 : 2;
    h->samplesPerFrame = layerBits == 3 ? 384
                       : (layerBits == 2 ? 1152 : (mpeg1 ? 1152 : 576));
    int slot = layerBits == 3 ? 4 : 1;
    h->frameLength = static_cast<int>(
            (static_cast<int64_t>(h->samplesPerFrame / 8) * h->bitrateBps) / h->sampleRate
            + static_cast<int64_t>(padding) * slot);
    return h->frameLength > 0;
}

bool parseMp3(const std::vector<unsigned char> &data, long *out) {
    for (size_t i = 0; i + 4 < data.size(); ++i) {
        Mp3Header h;
        if (!parseMp3Header(data.data() + i, &h)) continue;

        // 连续帧校验：按帧长跳到下一帧，必须连续命中多个合法帧头，
        // 避免 MP4/APE 等容器数据里偶然出现的 0xFF Ex 被误判成 MP3。
        int verified = 1;
        size_t next = i + static_cast<size_t>(h.frameLength);
        while (verified < 3 && next + 4 <= data.size()) {
            Mp3Header following;
            if (!parseMp3Header(data.data() + next, &following)) break;
            ++verified;
            next += static_cast<size_t>(following.frameLength);
        }
        if (verified < 3) continue;

        int64_t bitrate = h.bitrateBps;
        // VBR 文件首帧通常带 Xing/Info（或 VBRI）信息头，用它算平均码率；否则按首帧码率（CBR）处理。
        size_t scanEnd = std::min<size_t>(200, data.size() - i);
        for (size_t k = 4; k + 16 <= scanEnd; ++k) {
            const unsigned char *q = data.data() + i + k;
            bool xing = (q[0] == 'X' && q[1] == 'i' && q[2] == 'n' && q[3] == 'g') ||
                        (q[0] == 'I' && q[1] == 'n' && q[2] == 'f' && q[3] == 'o');
            bool vbri = q[0] == 'V' && q[1] == 'B' && q[2] == 'R' && q[3] == 'I';
            if (!xing && !vbri) continue;
            uint32_t frames = 0;
            uint32_t bytes = 0;
            if (xing) {
                uint32_t flags = readBe32(q + 4);
                frames = (flags & 0x01) ? readBe32(q + 8) : 0;
                bytes = (flags & 0x02) ? readBe32(q + 12) : 0;
            } else {
                bytes = readBe32(q + 10);
                frames = readBe32(q + 14);
            }
            int64_t totalSamples = static_cast<int64_t>(frames) * h.samplesPerFrame;
            if (frames > 0 && bytes > 0 && totalSamples > 0) {
                bitrate = static_cast<int64_t>(bytes) * 8 * h.sampleRate / totalSamples;
            }
            break;
        }

        out[0] = tierFromBitrate(static_cast<int>(bitrate));
        out[1] = static_cast<long>(std::min<int64_t>(bitrate, 0x7fffffff));
        out[2] = h.sampleRate;
        out[3] = 16;
        out[4] = h.channels;
        return true;
    }
    return false;
}

bool readHead(const std::string &path, int64_t offset, std::vector<unsigned char> *data) {
    std::ifstream input(path, std::ios::binary);
    if (!input) return false;
    if (offset > 0) input.seekg(offset);
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
    std::string filePath(raw);
    env->ReleaseStringUTFChars(path, raw);
    std::vector<unsigned char> data;
    long values[5] = {-1, 0, 0, 0, 0};
    int64_t fileSize = fileSizeBytes(filePath);
    int64_t offset = id3v2TagSize(filePath);
    bool ok = readHead(filePath, offset, &data) &&
              (parseFlac(filePath, data, fileSize, values) || parseWav(data, values) || parseMp3(data, values));
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
