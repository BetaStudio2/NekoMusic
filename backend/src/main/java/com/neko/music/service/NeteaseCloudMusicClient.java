package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Direct NetEase client with the weapi/eapi algorithms used by NeteaseCloudMusicApi. */
public class NeteaseCloudMusicClient {
    private static final Logger logger = LoggerFactory.getLogger(NeteaseCloudMusicClient.class);
    private static final String MUSIC_BASE = "https://music.163.com";
    private static final String INTERFACE_BASE = "https://interface.music.163.com";
    private static final String IV = "0102030405060708";
    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String EAPI_KEY = "e82ckenh8dichen8";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String PUBLIC_KEY_PEM = "-----BEGIN PUBLIC KEY-----\n"
            + "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB\n"
            + "-----END PUBLIC KEY-----";
    private static final PublicKey WEAPI_PUBLIC_KEY = loadPublicKey();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");

    private final ConfigManager config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NeteaseCloudMusicClient(ConfigManager config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(config.getNeteaseHttpTimeoutSeconds());
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public record NeteaseSongCandidate(long id, String title, String artist, String album) {}
    public record SongPlayUrl(String url, String type, int durationMs, String level) {}
    public record SongDetail(String title, String artist, String album, String coverUrl, int durationMs) {}
    public record LyricApiPayload(String primaryLrc, String lrcLyricRaw) {}

    public boolean isLoggedIn() {
        if (!parseCookie().containsKey("MUSIC_U")) return false;
        try {
            return postWeapi("/weapi/w/nuser/account/get", Map.of()).path("code").asInt(0) == 200;
        } catch (IOException e) {
            logger.warn("查询网易云登录状态失败: {}", e.getMessage());
            return false;
        }
    }

    public List<NeteaseSongCandidate> searchSongs(String keywords, int limit) throws IOException {
        JsonNode songs = searchRaw(keywords, 1, limit, 0).path("result").path("songs");
        if (!songs.isArray()) return List.of();
        List<NeteaseSongCandidate> result = new ArrayList<>();
        for (JsonNode song : songs) {
            long id = song.path("id").asLong(0);
            String title = textOrEmpty(song.path("name"));
            if (id > 0 && !title.isEmpty()) {
                result.add(new NeteaseSongCandidate(id, title, joinArtists(song.path("ar")),
                        textOrEmpty(song.path("al").path("name"))));
            }
        }
        return result;
    }

    public JsonNode searchRaw(String keywords, int type, int limit, int offset) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("s", keywords == null ? "" : keywords);
        data.put("type", type <= 0 ? 1 : type);
        data.put("limit", Math.max(1, Math.min(100, limit)));
        data.put("offset", Math.max(0, offset));
        return postWeapi("/weapi/search/get", data);
    }

    public Optional<SongDetail> fetchSongDetail(long songId) throws IOException {
        JsonNode songs = fetchSongDetailRaw(List.of(songId)).path("songs");
        if (!songs.isArray() || songs.isEmpty()) return Optional.empty();
        JsonNode song = songs.get(0);
        return Optional.of(new SongDetail(textOrEmpty(song.path("name")), joinArtists(song.path("ar")),
                textOrEmpty(song.path("al").path("name")), textOrEmpty(song.path("al").path("picUrl")),
                song.path("dt").asInt(0)));
    }

    public JsonNode fetchSongDetailRaw(List<Long> songIds) throws IOException {
        if (songIds == null || songIds.isEmpty()) {
            return objectMapper.createObjectNode().put("code", 400).put("msg", "ids 不能为空");
        }
        StringBuilder ids = new StringBuilder("[");
        for (int i = 0; i < songIds.size(); i++) {
            if (i > 0) ids.append(',');
            ids.append("{\"id\":").append(songIds.get(i)).append('}');
        }
        ids.append(']');
        return postWeapi("/weapi/v3/song/detail", Map.of("c", ids.toString()));
    }

    public Optional<SongPlayUrl> resolvePlayUrl(long songId, String preferredLevel) throws IOException {
        for (String requested : qualityFallbackChain(preferredLevel)) {
            JsonNode data = fetchSongUrlRaw(songId, requested).path("data");
            if (!data.isArray() || data.isEmpty()) continue;
            JsonNode item = data.get(0);
            if (item.path("code").asInt(0) != 200) continue;
            String url = textOrEmpty(item.path("url"));
            String actual = textOrEmpty(item.path("level")).toLowerCase(Locale.ROOT);
            if (url.isEmpty() || (!actual.isEmpty() && !actual.equals(requested))) continue;
            return Optional.of(new SongPlayUrl(url, textOrEmpty(item.path("type")).isEmpty() ? "mp3" : textOrEmpty(item.path("type")),
                    item.path("time").asInt(0), actual.isEmpty() ? requested : actual));
        }
        return Optional.empty();
    }

    public JsonNode fetchSongUrlRaw(long songId, String level) throws IOException {
        String requested = level == null || level.isBlank() ? "standard" : level.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ids", "[" + songId + "]");
        data.put("level", requested);
        data.put("encodeType", "flac");
        if ("sky".equals(requested)) data.put("immerseType", "c51");
        return postEapi("/api/song/enhance/player/url/v1", data);
    }

    public String fetchLyricLrc(long songId) throws IOException {
        return fetchLyricPayload(songId).primaryLrc();
    }

    public LyricApiPayload fetchLyricPayload(long songId) throws IOException {
        JsonNode root = fetchLyricRaw(songId);
        String raw = textOrEmpty(root.path("lrc").path("lyric"));
        return new LyricApiPayload(raw.isBlank() ? textOrEmpty(root.path("tlyric").path("lyric")) : raw, raw);
    }

    public JsonNode fetchLyricRaw(long songId) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", songId); data.put("tv", -1); data.put("lv", -1); data.put("rv", -1); data.put("kv", -1);
        return postApi("/api/song/lyric?_nmclfl=1", data);
    }

    /** 获取网易云歌单歌曲，响应兼容 NeteaseCloudMusicApi 的 /playlist/track/all。 */
    public JsonNode fetchPlaylistTrackAll(long playlistId, int limit, int offset) throws IOException {
        JsonNode playlistRoot = postApi("/api/v6/playlist/detail", Map.of("id", playlistId, "n", 100000, "s", 8));
        if (playlistRoot.path("code").asInt(0) != 200) return playlistRoot;
        JsonNode trackIds = playlistRoot.path("playlist").path("trackIds");
        if (!trackIds.isArray()) return objectMapper.createObjectNode().put("code", 404).put("msg", "歌单不存在");
        int start = Math.max(0, offset);
        int end = Math.min(trackIds.size(), start + (limit <= 0 ? 5000 : Math.min(limit, 5000)));
        ArrayNode songs = objectMapper.createArrayNode();
        ArrayNode privileges = objectMapper.createArrayNode();
        for (int cursor = start; cursor < end; cursor += 1000) {
            int batchEnd = Math.min(end, cursor + 1000);
            List<Long> ids = new ArrayList<>(batchEnd - cursor);
            for (int i = cursor; i < batchEnd; i++) ids.add(trackIds.get(i).path("id").asLong(0));
            JsonNode details = fetchSongDetailRaw(ids);
            if (details.path("songs").isArray()) details.path("songs").forEach(songs::add);
            if (details.path("privileges").isArray()) details.path("privileges").forEach(privileges::add);
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.set("songs", songs); result.set("privileges", privileges); result.put("code", 200);
        return result;
    }

    public JsonNode fetchPlaylistDetail(long playlistId) throws IOException {
        return postApi("/api/v6/playlist/detail", Map.of("id", playlistId, "n", 100000, "s", 8));
    }

    /** 下载进度回调：totalBytes 为 -1 表示上游未返回 Content-Length。 */
    @FunctionalInterface
    public interface DownloadProgressListener {
        void onProgress(long bytesRead, long totalBytes);
    }

    public void downloadToFile(String url, Path destination) throws IOException {
        downloadToFile(url, destination, null);
    }

    public void downloadToFile(String url, Path destination, DownloadProgressListener listener) throws IOException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getNeteaseHttpTimeoutSeconds())).header("User-Agent", userAgent()).GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) throw new IOException("下载失败 HTTP " + response.statusCode());
            long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            Path parent = destination.getParent(); if (parent != null) Files.createDirectories(parent);
            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[64 * 1024];
                long bytesRead = 0;
                long lastReported = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    bytesRead += read;
                    if (listener != null && (bytesRead - lastReported >= 512 * 1024 || bytesRead == totalBytes)) {
                        lastReported = bytesRead;
                        listener.onProgress(bytesRead, totalBytes);
                    }
                }
                if (listener != null && lastReported != bytesRead) {
                    listener.onProgress(bytesRead, totalBytes);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("下载被中断", e);
        }
    }

    private JsonNode postApi(String path, Map<String, ?> data) throws IOException {
        return postForm(MUSIC_BASE + path, encodeForm(data), false, false);
    }

    private JsonNode postWeapi(String path, Map<String, ?> data) throws IOException {
        String text = objectMapper.writeValueAsString(data);
        String secret = randomSecretKey();
        Map<String, String> encrypted = new LinkedHashMap<>();
        encrypted.put("params", aesCbcBase64(aesCbcBase64(text, PRESET_KEY, IV), secret, IV));
        encrypted.put("encSecKey", rsaNoPaddingHex(new StringBuilder(secret).reverse().toString()));
        return postForm(MUSIC_BASE + path, encodeForm(encrypted), true, false);
    }

    private JsonNode postEapi(String path, Map<String, ?> data) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>(data);
        Map<String, String> header = new LinkedHashMap<>();
        header.put("osver", "17,1,2"); header.put("appver", "8.10.05"); header.put("versioncode", "140");
        header.put("buildver", String.valueOf(System.currentTimeMillis() / 1000)); header.put("resolution", "1920x1080");
        header.put("__csrf", parseCookie().getOrDefault("__csrf", "")); header.put("os", "android");
        payload.put("header", header);
        String text = objectMapper.writeValueAsString(payload);
        String digest = md5Hex("nobody" + path + "use" + text + "md5forencrypt");
        String encrypted = aesEcbHex(path + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest, EAPI_KEY);
        return postForm(INTERFACE_BASE + path.replace("/api/", "/eapi/"), encodeForm(Map.of("params", encrypted)), false, true);
    }

    private JsonNode postForm(String url, String body, boolean weapi, boolean eapi) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getNeteaseHttpTimeoutSeconds()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", weapi ? weapiUserAgent() : userAgent())
                .header("Referer", MUSIC_BASE).header("Cookie", cookieHeader(eapi))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("Netease API HTTP " + response.statusCode());
            String raw = eapi ? decryptEapi(response.body()) : response.body();
            return objectMapper.readTree(raw);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("请求被中断", e);
        }
    }

    private String cookieHeader(boolean eapi) {
        Map<String, String> cookies = parseCookie();
        if (!eapi) {
            if (cookies.isEmpty()) return "__remember_me=true; NMTID=guest";
            cookies.put("__remember_me", "true"); return stringifyCookie(cookies);
        }
        Map<String, String> header = new LinkedHashMap<>();
        header.put("osver", "17,1,2"); header.put("appver", "8.10.05"); header.put("versioncode", "140");
        header.put("buildver", String.valueOf(System.currentTimeMillis() / 1000)); header.put("resolution", "1920x1080");
        header.put("__csrf", cookies.getOrDefault("__csrf", "")); header.put("os", "android");
        if (cookies.containsKey("MUSIC_U")) header.put("MUSIC_U", cookies.get("MUSIC_U"));
        if (cookies.containsKey("MUSIC_A")) header.put("MUSIC_A", cookies.get("MUSIC_A"));
        return stringifyCookie(header);
    }

    private Map<String, String> parseCookie() {
        Map<String, String> result = new LinkedHashMap<>();
        String cookie = config.getNeteaseCookie(); if (cookie == null) return result;
        for (String pair : cookie.split(";")) { int eq = pair.indexOf('='); if (eq > 0) result.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim()); }
        return result;
    }

    private static String stringifyCookie(Map<String, String> cookies) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            if (!result.isEmpty()) result.append("; "); result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }

    private static String encodeForm(Map<String, ?> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!result.isEmpty()) result.append('&');
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static String randomSecretKey() {
        StringBuilder key = new StringBuilder(16); for (int i = 0; i < 16; i++) key.append(BASE62.charAt(RANDOM.nextInt(BASE62.length()))); return key.toString();
    }

    private static String aesCbcBase64(String text, String key, String iv) throws IOException { return encryptAes(text.getBytes(StandardCharsets.UTF_8), key, "CBC", iv, true); }
    private static String aesEcbHex(String text, String key) throws IOException { return encryptAes(text.getBytes(StandardCharsets.UTF_8), key, "ECB", "", false).toUpperCase(Locale.ROOT); }

    private static String encryptAes(byte[] plain, String key, String mode, String iv, boolean base64) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/" + mode + "/PKCS5Padding");
            SecretKeySpec secret = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            if ("CBC".equals(mode)) cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
            else cipher.init(Cipher.ENCRYPT_MODE, secret);
            byte[] encrypted = cipher.doFinal(plain); return base64 ? Base64.getEncoder().encodeToString(encrypted) : HexFormat.of().formatHex(encrypted);
        } catch (Exception e) { throw new IOException("网易云 AES 加密失败", e); }
    }

    private static String rsaNoPaddingHex(String text) throws IOException {
        try { Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, WEAPI_PUBLIC_KEY); return HexFormat.of().formatHex(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IOException("网易云 RSA 加密失败", e); }
    }

    private static String decryptEapi(String raw) throws IOException {
        try {
            String value = raw == null ? "" : raw.trim(); if (value.startsWith("{")) return value;
            byte[] encrypted = HEX.matcher(value).matches() && value.length() % 2 == 0 ? HexFormat.of().parseHex(value) : value.getBytes(StandardCharsets.ISO_8859_1);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding"); cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(EAPI_KEY.getBytes(StandardCharsets.UTF_8), "AES"));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IOException("网易云 eapi 响应解密失败", e); }
    }

    private static String md5Hex(String text) throws IOException {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IOException("网易云 MD5 计算失败", e); }
    }

    private static PublicKey loadPublicKey() {
        try {
            String body = PUBLIC_KEY_PEM.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static List<String> qualityFallbackChain(String preferred) {
        String p = preferred == null ? "" : preferred.trim().toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>(); if (!p.isEmpty()) result.add(p);
        for (String level : List.of("jymaster", "hires", "jyeffect", "sky", "lossless", "exhigh", "higher", "standard")) if (!result.contains(level)) result.add(level);
        return result;
    }

    private static String joinArtists(JsonNode artists) {
        if (!artists.isArray()) return ""; StringBuilder result = new StringBuilder();
        for (JsonNode artist : artists) { String name = textOrEmpty(artist.path("name")); if (name.isEmpty()) continue; if (!result.isEmpty()) result.append(" / "); result.append(name); }
        return result.toString();
    }

    private static String textOrEmpty(JsonNode node) { return node == null || node.isNull() || node.isMissingNode() ? "" : node.asText("").trim(); }
    private static String userAgent() { return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"; }
    private static String weapiUserAgent() { return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/116 Safari/537.36"; }
}
