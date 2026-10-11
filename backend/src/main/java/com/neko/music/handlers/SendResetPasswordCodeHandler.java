package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.service.AuthAbuseLimiter;
import com.neko.music.service.UserAuthService;
import com.neko.music.util.ClientIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * 发送重置密码验证码处理器。
 *
 * <p><b>账号枚举防护</b>：本接口对「邮箱是否注册」必须给出完全一致的响应（状态码 / 结构 / 文案 /
 * 耗时），因此：</p>
 * <ul>
 *   <li>限流在存在性判断**之前**完成，且来源 IP 与邮箱槽位对注册与否一视同仁——否则 {@code 429}
 *       本身就会变成账号存在性信号；</li>
 *   <li>两个分支返回同一文案，邮箱是否存在只决定「是否真的发信」；</li>
 *   <li>发信异步执行，避免已注册邮箱因多走一次 SMTP 而在响应时间上暴露。</li>
 * </ul>
 * <p>发信细节只进服务端日志，不进入对外响应。</p>
 */
@WebServlet("/user/send-reset-code")
public class SendResetPasswordCodeHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(SendResetPasswordCodeHandler.class);

    /**
     * 统一响应文案：不区分邮箱是否注册。中性表述既完成账号枚举防护，也仍能提示正常用户查收邮件；
     * 未注册邮箱不会收到任何邮件，客户端在下一步校验验证码时会得到与「验证码错误」一致的回应。
     */
    static final String UNIFORM_MESSAGE = "如果该邮箱已注册，我们已发送重置密码验证码，请查收邮件";

    private UserAuthService userAuthService;

    @Override
    public void init() throws ServletException {
        userAuthService = Main.getUserAuthService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        if (enforceAuthAbuseLimit(request, response, AuthAbuseLimiter.Scope.SEND_CODE)) {
            return;
        }

        String requestBody = readBody(request); // ApiServlet 统一实现

        try {
            JsonNode requestData = Main.getObjectMapper().readTree(requestBody);

            String email = null;
            String nickname = "用户"; // 默认昵称

            if (requestData != null && requestData.has("email")) {
                email = requestData.get("email").asText();
            }

            // 验证请求参数
            if (email == null || email.trim().isEmpty()) {
                sendResponse(response, false, "邮箱不能为空", null);
                return;
            }

            // 验证邮箱格式
            if (!isValidEmail(email)) {
                sendResponse(response, false, "邮箱格式不正确", null);
                return;
            }

            // 统一限流：与邮箱是否注册无关，且必须先于存在性判断（否则 429 会成为枚举信号）
            Optional<Long> waitFor = userAuthService.acquireResetCodeSlot(email,
                    ClientIpResolver.clientIp(request));
            if (waitFor.isPresent()) {
                sendCooldownResponse(response, waitFor.get());
                return;
            }

            // 邮箱是否存在只决定「是否真的发信」，不进入对外响应；发信异步执行，响应耗时保持一致
            if (userAuthService.userExistsByEmail(email)) {
                try {
                    userAuthService.sendResetPasswordCodeAsync(email, nickname);
                    logger.info("重置密码验证码已受理，已提交发信: {}", email);
                } catch (RuntimeException e) {
                    // 提交发信失败同样不能与「未注册」分支产生差异，只记服务端日志
                    logger.error("提交重置密码验证码发信失败: {}", email, e);
                }
            } else {
                logger.info("重置密码验证码已受理，邮箱未注册不发送: {}", email);
            }

            sendResponse(response, true, UNIFORM_MESSAGE, null);

        } catch (Exception e) {
            logger.error("处理发送重置密码验证码请求时发生错误", e);
            sendResponse(response, false, "服务器内部错误", null);
        }
    }

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }


    /**
     * 发送JSON响应
     */
}
