package com.neko.music.handlers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.util.ClientReleaseStorage;
import com.neko.music.util.SiteUrlResolver;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 管理员上传客户端安装包到 releases 目录（仅 super_admin / admin） */
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = ClientReleaseStorage.MAX_UPLOAD_BYTES,
        maxRequestSize = ClientReleaseStorage.MAX_UPLOAD_BYTES + 1024 * 1024
)
public class AdminClientReleaseUploadHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminClientReleaseUploadHandler.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            writeJson(response, false, "未授权访问");
            return;
        }
        if (!Main.getAdminAuthService().canUploadClientReleaseByToken(token)) {
            response.setStatus(HttpStatus.FORBIDDEN_403);
            writeJson(response, false, "需要管理员及以上权限");
            return;
        }

        Part filePart = null;
        for (Part part : request.getParts()) {
            if ("file".equals(part.getName()) && part.getSize() != 0) {
                filePart = part;
                break;
            }
        }
        if (filePart == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeJson(response, false, "请上传 file 字段");
            return;
        }

        String submitted = filePart.getSubmittedFileName();
        String fileName = submitted == null ? "" : Path.of(submitted).getFileName().toString();
        if (fileName.isBlank()) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeJson(response, false, "无法识别文件名");
            return;
        }

        long declaredSize = filePart.getSize();
        Path target = null;
        try {
            target = ClientReleaseStorage.resolveTargetForUpload(fileName);
            if (declaredSize >= 0) {
                ClientReleaseStorage.validateUpload(fileName, declaredSize);
                try (InputStream in = filePart.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                try (InputStream in = filePart.getInputStream()) {
                    long written = copyWithSizeLimit(in, target, ClientReleaseStorage.MAX_UPLOAD_BYTES);
                    ClientReleaseStorage.validateUpload(fileName, written);
                }
            }

            long size = Files.size(target);
            if (size > ClientReleaseStorage.MAX_UPLOAD_BYTES) {
                Files.deleteIfExists(target);
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                writeJson(response, false, "安装包体积不得超过 50MiB");
                return;
            }

            String downloadUrl = ClientReleaseStorage.publicDownloadUrl(
                    SiteUrlResolver.resolvePublicSiteBase(request), fileName);

            ObjectNode data = Main.getObjectMapper().createObjectNode();
            data.put("fileName", fileName);
            data.put("size", size);
            data.put("downloadUrl", downloadUrl);
            data.put("storagePath", target.toString());

            ObjectNode root = Main.getObjectMapper().createObjectNode();
            root.put("success", true);
            root.put("message", "上传成功");
            root.set("data", data);

            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write(Main.getObjectMapper().writeValueAsString(root));
            logger.info("管理员上传安装包: {} ({} bytes)", fileName, size);
        } catch (IllegalArgumentException e) {
            if (target != null) {
                Files.deleteIfExists(target);
            }
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeJson(response, false, e.getMessage());
        } catch (Exception e) {
            if (target != null) {
                Files.deleteIfExists(target);
            }
            logger.error("上传安装包失败 fileName={}", fileName, e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            writeJson(response, false, "上传失败: " + e.getMessage());
        }
    }

    private static long copyWithSizeLimit(InputStream in, Path target, long maxBytes) throws IOException {
        Files.createDirectories(target.getParent());
        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("安装包体积不得超过 50MiB");
                }
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        return total;
    }

    private void writeJson(HttpServletResponse response, boolean success, String message) throws IOException {
        ObjectNode root = Main.getObjectMapper().createObjectNode();
        root.put("success", success);
        root.put("message", message);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(Main.getObjectMapper().writeValueAsString(root));
    }
}
