package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.i18n.ApiMessages;
import com.neko.music.service.UserManagementException;
import com.neko.music.service.UserManagementService;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.ImageUploadValidator;
import com.neko.music.util.RequestAuthUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** 当前登录用户上传头像：{@code POST /api/user/avatar/upload}。 */
public final class UserAvatarUploadHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(UserAvatarUploadHandler.class);
    private static final String DEFAULT_AVATAR_DIR = "avatars";
    private static final long MAX_FILE_SIZE = ImageUploadValidator.DEFAULT_MAX_IMAGE_BYTES;

    private final UserManagementService service;
    private final Function<HttpServletRequest, Integer> authResolver;
    private final Path avatarDirectory;

    public UserAvatarUploadHandler() {
        this(Main.getUserManagementService(), RequestAuthUtil::authenticate, Paths.get(DEFAULT_AVATAR_DIR));
    }

    UserAvatarUploadHandler(
            UserManagementService service,
            Function<HttpServletRequest, Integer> authResolver,
            Path avatarDirectory) {
        this.service = service;
        this.authResolver = authResolver;
        this.avatarDirectory = avatarDirectory;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        Integer userId = authResolver.apply(request);
        if (userId == null) {
            String token = RequestAuthUtil.extractToken(request);
            sendErrorObject(response, HttpStatus.UNAUTHORIZED_401,
                    token == null ? "auth.unauthorized" : "auth.invalid_token");
            return;
        }

        Path uploadedFile = null;
        try {
            request.setCharacterEncoding("UTF-8");
            Part avatarPart = request.getPart("avatar");
            if (avatarPart == null || avatarPart.getSize() == 0) {
                sendErrorObject(response, HttpStatus.BAD_REQUEST_400, "user.avatar.no_file");
                return;
            }

            ImageUploadValidator.ValidationResult imageValidation =
                    ImageUploadValidator.validatePart(avatarPart, MAX_FILE_SIZE);
            if (!imageValidation.isValid()) {
                sendErrorObject(response, HttpStatus.BAD_REQUEST_400, imageValidation.getErrorMessage());
                return;
            }

            Files.createDirectories(avatarDirectory);
            String uniqueFileName = userId + "_" + UUID.randomUUID() + imageValidation.getExtension();
            uploadedFile = avatarDirectory.resolve(uniqueFileName);
            try (InputStream inputStream = avatarPart.getInputStream()) {
                Files.copy(inputStream, uploadedFile, StandardCopyOption.REPLACE_EXISTING);
            }

            UserManagementService.AvatarChange change;
            try {
                change = service.changeUserAvatar(userId, uploadedFile.toString());
            } catch (UserManagementException e) {
                deleteQuietly(uploadedFile, "数据库更新失败后清理新头像");
                sendErrorObject(response, HttpStatus.INTERNAL_SERVER_ERROR_500,
                        "user.avatar.db_update_failed");
                return;
            }

            deletePreviousAvatar(change.previousAvatarPath(), uploadedFile);
            logger.info("用户 {} 上传头像成功: {}", userId, uniqueFileName);
            sendUploadSuccess(response, uploadedFile.toString());
        } catch (Exception e) {
            if (uploadedFile != null) {
                deleteQuietly(uploadedFile, "上传失败后清理新头像");
            }
            logger.error("上传头像时出错", e);
            sendErrorObject(response, HttpStatus.INTERNAL_SERVER_ERROR_500,
                    ApiMessages.translate("user.avatar.upload_failed", e.getMessage()));
        }
    }

    private void deletePreviousAvatar(String previousAvatarPath, Path currentFile) {
        if (previousAvatarPath == null || previousAvatarPath.isBlank()) {
            return;
        }

        Path root = avatarDirectory.toAbsolutePath().normalize();
        Path previous = Paths.get(previousAvatarPath);
        Path target = previous.isAbsolute()
                ? previous.normalize()
                : Paths.get(System.getProperty("user.dir")).resolve(previous).normalize();
        if (!target.startsWith(root)) {
            logger.warn("拒绝删除头像目录之外的旧文件: {}", previousAvatarPath);
            return;
        }
        if (target.equals(currentFile.toAbsolutePath().normalize())) {
            return;
        }
        deleteQuietly(target, "删除旧头像");
    }

    private static void deleteQuietly(Path file, String action) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("{}失败: {}", action, file, e);
        }
    }

    private void sendUploadSuccess(HttpServletResponse response, String avatarPath) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", ApiMessages.translate("user.avatar.upload_ok"));
        body.put("avatarPath", avatarPath);
        writeJson(response, HttpStatus.OK_200, MAPPER.writeValueAsString(body));
    }
}
