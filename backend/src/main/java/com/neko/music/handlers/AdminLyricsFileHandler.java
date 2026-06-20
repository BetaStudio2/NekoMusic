package com.neko.music.handlers;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.util.AdminPermissionUtil;
import com.neko.music.util.MusicAssetLocator;
import com.neko.music.util.PermissionHelper;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 管理后台歌词文件管理器：仅允许操作 Music/lyrics 目录下的 .lrc 文件。
 */
public class AdminLyricsFileHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminLyricsFileHandler.class);
    private static final Pattern MUSIC_ID_FILE = Pattern.compile("(\\d+)\\.lrc", Pattern.CASE_INSENSITIVE);
    private static final long MAX_LYRICS_BYTES = 1024L * 1024L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response, AdminPermissionUtil.Permission.MUSIC_VIEW)) {
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/tree")) {
            listTree(response);
            return;
        }

        if (pathInfo.startsWith("/file/")) {
            readFile(pathInfo.substring("/file/".length()), response);
            return;
        }

        response.setStatus(HttpStatus.NOT_FOUND_404);
        writeFailure(response, "接口不存在");
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response, AdminPermissionUtil.Permission.MUSIC_EDIT)) {
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.startsWith("/file/")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "缺少文件路径");
            return;
        }

        SaveLyricsRequest saveRequest;
        try {
            String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            saveRequest = Main.getObjectMapper().readValue(body, SaveLyricsRequest.class);
        } catch (Exception e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "请求格式错误");
            return;
        }

        if (saveRequest.content == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "歌词内容不能为空");
            return;
        }
        if (saveRequest.content.getBytes(StandardCharsets.UTF_8).length > MAX_LYRICS_BYTES) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "歌词文件不能超过 1MB");
            return;
        }

        Path file = resolveLyricsFile(pathInfo.substring("/file/".length()));
        if (file == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "无效的歌词文件路径");
            return;
        }

        Files.createDirectories(MusicAssetLocator.lyricsDir());
        if (!isSafeLyricsPath(file)) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "无效的歌词文件路径");
            return;
        }

        Files.createDirectories(file.getParent());
        Files.writeString(file, saveRequest.content, StandardCharsets.UTF_8);
        rebuildLyricsIndex(file);

        ObjectNode data = buildFileNode(file, musicMetaById());
        writeSuccess(response, "保存成功", data);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response, AdminPermissionUtil.Permission.MUSIC_EDIT)) {
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || !pathInfo.startsWith("/file/")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "缺少文件路径");
            return;
        }

        Path file = resolveLyricsFile(pathInfo.substring("/file/".length()));
        if (file == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "无效的歌词文件路径");
            return;
        }
        if (!Files.exists(file)) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            writeFailure(response, "歌词文件不存在");
            return;
        }

        Integer musicId = musicIdFromFile(file.getFileName().toString());
        Files.delete(file);
        if (musicId != null && Main.getLyricsSearchIndex() != null) {
            Main.getLyricsSearchIndex().rebuildOne(musicId);
        }

        writeSuccess(response, "删除成功", Main.getObjectMapper().createObjectNode());
    }

    private void listTree(HttpServletResponse response) throws IOException {
        Path lyricsDir = MusicAssetLocator.lyricsDir();
        Files.createDirectories(lyricsDir);

        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(lyricsDir)) {
            stream
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isLrcFile)
                    .filter(this::isSafeLyricsPath)
                    .sorted(Comparator.comparing(path -> relativePath(path).toLowerCase(Locale.ROOT)))
                    .forEach(files::add);
        }

        ObjectNode root = Main.getObjectMapper().createObjectNode();
        root.put("type", "directory");
        root.put("name", "lyrics");
        root.put("path", "");
        root.set("children", Main.getObjectMapper().createArrayNode());

        Map<Integer, MusicMeta> metaById = musicMetaById();
        for (Path file : files) {
            appendFile(root, file, metaById);
        }

        ObjectNode data = Main.getObjectMapper().createObjectNode();
        data.set("tree", root);
        data.put("totalFiles", files.size());
        data.put("basePath", MusicAssetLocator.LYRICS_REL_DIR);
        writeSuccess(response, "查询成功", data);
    }

    private void readFile(String encodedPath, HttpServletResponse response) throws IOException {
        Path file = resolveLyricsFile(encodedPath);
        if (file == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "无效的歌词文件路径");
            return;
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !isSafeLyricsPath(file)) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            writeFailure(response, "歌词文件不存在");
            return;
        }
        long size = Files.size(file);
        if (size > MAX_LYRICS_BYTES) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeFailure(response, "歌词文件过大，拒绝在线编辑");
            return;
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);
        ObjectNode data = buildFileNode(file, musicMetaById());
        data.put("content", content);
        writeSuccess(response, "读取成功", data);
    }

    private void appendFile(ObjectNode root, Path file, Map<Integer, MusicMeta> metaById) throws IOException {
        String relative = relativePath(file);
        String[] parts = relative.split("/");
        ObjectNode current = root;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean isFile = i == parts.length - 1;
            ArrayNode children = (ArrayNode) current.get("children");

            ObjectNode existing = null;
            for (int j = 0; j < children.size(); j++) {
                ObjectNode child = (ObjectNode) children.get(j);
                if (part.equals(child.get("name").asText())) {
                    existing = child;
                    break;
                }
            }

            if (existing == null) {
                existing = isFile
                        ? buildFileNode(file, metaById)
                        : buildDirectoryNode(part, joinPath(parts, i + 1));
                children.add(existing);
            }
            current = existing;
        }
    }

    private ObjectNode buildDirectoryNode(String name, String path) {
        ObjectNode node = Main.getObjectMapper().createObjectNode();
        node.put("type", "directory");
        node.put("name", name);
        node.put("path", path);
        node.set("children", Main.getObjectMapper().createArrayNode());
        return node;
    }

    private ObjectNode buildFileNode(Path file, Map<Integer, MusicMeta> metaById) throws IOException {
        String fileName = file.getFileName().toString();
        Integer musicId = musicIdFromFile(fileName);
        MusicMeta meta = musicId == null ? null : metaById.get(musicId);

        ObjectNode node = Main.getObjectMapper().createObjectNode();
        node.put("type", "file");
        node.put("name", fileName);
        node.put("path", relativePath(file));
        node.put("size", Files.exists(file) ? Files.size(file) : 0L);
        node.put("updatedAt", Files.exists(file) ? Files.getLastModifiedTime(file).toInstant().toString() : Instant.EPOCH.toString());
        if (musicId == null) {
            node.putNull("musicId");
        } else {
            node.put("musicId", musicId);
        }
        if (meta == null) {
            node.putNull("title");
            node.putNull("artist");
            node.put("existsInDb", false);
            node.put("displayName", fileName);
        } else {
            node.put("title", meta.title);
            node.put("artist", meta.artist);
            node.put("existsInDb", true);
            node.put("displayName", musicId + " - " + meta.title);
        }
        return node;
    }

    private Map<Integer, MusicMeta> musicMetaById() {
        Map<Integer, MusicMeta> metaById = new HashMap<>();
        String sql = "SELECT id, title, artist FROM music";
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                metaById.put(rs.getInt("id"), new MusicMeta(rs.getString("title"), rs.getString("artist")));
            }
        } catch (Exception e) {
            logger.warn("读取音乐元数据失败: {}", e.toString());
        }
        return metaById;
    }

    private Path resolveLyricsFile(String encodedPath) {
        try {
            String decoded = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8);
            decoded = decoded.replace('\\', '/');
            while (decoded.startsWith("/")) {
                decoded = decoded.substring(1);
            }
            if (decoded.isBlank() || decoded.contains("\0") || !decoded.toLowerCase(Locale.ROOT).endsWith(".lrc")) {
                return null;
            }

            Path base = MusicAssetLocator.lyricsDir().toAbsolutePath().normalize();
            Path file = base.resolve(decoded).normalize();
            if (!MusicAssetLocator.isUnderDirectory(file, base)) {
                return null;
            }
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSafeLyricsPath(Path file) {
        try {
            Path base = MusicAssetLocator.lyricsDir().toAbsolutePath().normalize();
            if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(base)) {
                return false;
            }
            Path realBase = Files.exists(base) ? base.toRealPath() : base;
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(file)) {
                    return false;
                }
                return file.toRealPath().startsWith(realBase);
            }

            Path existingParent = file.toAbsolutePath().normalize().getParent();
            while (existingParent != null && !Files.exists(existingParent, LinkOption.NOFOLLOW_LINKS)) {
                existingParent = existingParent.getParent();
            }
            return existingParent != null && existingParent.toRealPath().startsWith(realBase);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLrcFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lrc");
    }

    private String relativePath(Path path) {
        return MusicAssetLocator.lyricsDir()
                .toAbsolutePath()
                .normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private static String joinPath(String[] parts, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    private Integer musicIdFromFile(String fileName) {
        Matcher matcher = MUSIC_ID_FILE.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void rebuildLyricsIndex(Path file) {
        Integer musicId = musicIdFromFile(file.getFileName().toString());
        if (musicId != null && Main.getLyricsSearchIndex() != null) {
            Main.getLyricsSearchIndex().rebuildOne(musicId);
        }
    }

    private void writeSuccess(HttpServletResponse response, String message, ObjectNode data) throws IOException {
        ObjectNode root = Main.getObjectMapper().createObjectNode();
        root.put("success", true);
        root.put("message", message);
        root.set("data", data);
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(Main.getObjectMapper().writeValueAsString(root));
    }

    private void writeFailure(HttpServletResponse response, String message) throws IOException {
        ObjectNode root = Main.getObjectMapper().createObjectNode();
        root.put("success", false);
        root.put("message", message);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(Main.getObjectMapper().writeValueAsString(root));
    }

    private static class SaveLyricsRequest {
        public String content;
    }

    private record MusicMeta(String title, String artist) {
    }
}
