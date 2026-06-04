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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 管理员上传客户端安装包到 releases 目录 */
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 2L * 1024 * 1024 * 1024, maxRequestSize = 2L * 1024 * 1024 * 1024)
public class AdminClientReleaseUploadHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminClientReleaseUploadHandler.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!isAdminAuthorized(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            writeJson(response, false, "未授权访问");
            return;
        }

        Part filePart = null;
        for (Part part : request.getParts()) {
            if ("file".equals(part.getName()) && part.getSize() > 0) {
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

        try {
            Path target = ClientReleaseStorage.resolveTargetForUpload(fileName);
            try (InputStream in = filePart.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            long size = Files.size(target);
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
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            writeJson(response, false, e.getMessage());
        } catch (Exception e) {
            logger.error("上传安装包失败 fileName={}", fileName, e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            writeJson(response, false, "上传失败: " + e.getMessage());
        }
    }

    private boolean isAdminAuthorized(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            return false;
        }
        return Main.getAdminAuthService().validateAdminToken(token);
    }

    private void writeJson(HttpServletResponse response, boolean success, String message) throws IOException {
        ObjectNode root = Main.getObjectMapper().createObjectNode();
        root.put("success", success);
        root.put("message", message);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(Main.getObjectMapper().writeValueAsString(root));
    }
}
