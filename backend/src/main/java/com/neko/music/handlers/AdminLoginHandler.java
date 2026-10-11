package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.model.Admin;
import com.neko.music.service.AdminAuthService;
import com.neko.music.service.AdminLoginThrottleService;
import com.neko.music.util.ClientIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 管理端登录。
 *
 * <p>两条防线缺一不可：</p>
 * <ul>
 *   <li>口令校验等时（{@link AdminAuthService#authenticate}）——用户名不存在时同样跑一次同成本
 *       Argon2，杜绝「秒回 401」这个用户名枚举时间侧信道；</li>
 *   <li>失败限流与锁定（{@link AdminLoginThrottleService}）——等时校验会让不存在的用户名也消耗
 *       Argon2，必须配合限流，同时阻断慢速口令爆破。限流计数只与请求本身有关、与账号是否存在无关，
 *       因此不会重新引出枚举面。</li>
 * </ul>
 */
public class AdminLoginHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(AdminLoginHandler.class);

    private AdminAuthService adminAuthService;
    private AdminLoginThrottleService throttleService;

    @Override
    public void init() throws ServletException {
        adminAuthService = Main.getAdminAuthService();
        throttleService = Main.getAdminLoginThrottleService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");

        // 处理预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        try {
            // 读取请求体
            String requestBody = readBody(request);

            // 解析JSON请求体
            Map<String, String> requestData = Main.getObjectMapper().readValue(requestBody.toString(), Map.class);
            String username = requestData.get("username");
            String password = requestData.get("password");

            // 验证参数
            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                sendErrorResponse(response, 400, "用户名和密码不能为空");
                return;
            }

            String name = username.trim();
            String clientIp = ClientIpResolver.clientIp(request);

            // 限流检查必须早于口令校验：被锁定时对「存在的用户名」与「不存在的用户名」返回完全一致
            if (throttleService != null) {
                Optional<Long> locked = throttleService.lockRemaining(name, clientIp);
                if (locked.isPresent()) {
                    logger.warn("管理端登录已被限流: username={} ip={}", name, clientIp);
                    long retryAfterSec = locked.get();
                    response.setHeader("Retry-After", String.valueOf(retryAfterSec));
                    sendErrorResponse(response, 429,
                            "登录失败次数过多，请 " + retryAfterSec + " 秒后再试");
                    return;
                }
            }

            // init() 未执行时（理论上不会）退回全局单例，保证行为不变
            AdminAuthService authService = adminAuthService != null ? adminAuthService : Main.getAdminAuthService();

            // 验证管理员凭据
            Optional<Admin> adminOpt = authService.authenticate(name, password);

            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();

                if (throttleService != null) {
                    throttleService.clearFailures(name);
                }

                // 创建会话令牌
                String sessionToken = authService.createAdminSession(admin);
                if (sessionToken == null) {
                    sendErrorResponse(response, 500, "创建会话失败");
                    return;
                }
                
                // 创建成功响应
                Map<String, Object> successResponse = new HashMap<>();
                successResponse.put("success", true);
                successResponse.put("message", "登录成功");
                successResponse.put("token", sessionToken);  // 返回会话令牌
                successResponse.put("admin", Map.of(
                    "id", admin.id(),
                    "username", admin.username(),
                    "email", admin.email(),
                    "role", admin.role(),
                    "createdAt", admin.createdAt(),
                    "lastLoginAt", admin.lastLoginAt()
                ));
                
                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter out = response.getWriter();
                out.print(Main.getObjectMapper().writeValueAsString(successResponse));
                out.flush();
            } else {
                // 失败计数与账号是否存在无关：不存在的用户名同样累计，因此 429 不是枚举信号
                if (throttleService != null) {
                    throttleService.recordFailure(name, clientIp);
                }
                sendErrorResponse(response, 401, "用户名或密码错误");
            }
        } catch (Exception e) {
            logger.error("管理员登录处理错误", e);
            sendErrorResponse(response, 500, "服务器内部错误");
        }
    }

}