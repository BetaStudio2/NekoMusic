package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.config.ConfigManager;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final ConfigManager configManager;
    private final String webhookUrl;
    private final String authToken;
    
    public NotificationService(ConfigManager configManager) {
        this.configManager = configManager;
        String url = configManager.getMsgUrl();
        // 自动添加 http:// 前缀（如果缺少）
        if (url != null && !url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        // 添加 /send 路径
        if (url != null && !url.isEmpty() && !url.endsWith("/send")) {
            url = url + "/send";
        }
        this.webhookUrl = url;
        this.authToken = configManager.getMsgToken();
        
        logger.info("NotificationService 初始化完成, Webhook URL: {}", webhookUrl);
    }
    
    /**
     * 发送通知
     * @param message 通知消息内容
     * @return 是否发送成功
     */
    public boolean sendNotification(String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.warn("Webhook URL 未配置，跳过发送通知");
            return false;
        }
        
        if (authToken == null || authToken.isEmpty()) {
            logger.warn("Auth Token 未配置，跳过发送通知");
            return false;
        }
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 构建 JSON 请求体
            String jsonBody = String.format("{\"message\": %s}", 
                objectMapper.writeValueAsString(message));
            
            // 创建 POST 请求
            HttpPost httpPost = new HttpPost(webhookUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + authToken);
            httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
            
            // 发送请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    logger.info("通知发送成功: {}", message);
                    return true;
                } else {
                    logger.warn("通知发送失败，状态码: {}", statusCode);
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("发送通知时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 发送音乐审核通过通知
     * @param musicTitle 音乐标题
     * @param artist 艺术家
     * @param uploadUserId 上传用户ID
     * @return 是否发送成功
     */
    public boolean sendMusicApprovedNotification(String musicTitle, String artist, int uploadUserId) {
        String message = String.format("音乐审核提醒\n标题: %s\n艺术家: %s\n上传用户ID: %d", 
            musicTitle, artist, uploadUserId);
        return sendNotification(message);
    }
    
    /**
     * 发送音乐上传完成通知
     * @param musicTitle 音乐标题
     * @param artist 艺术家
     * @param uploadUserId 上传用户ID
     * @return 是否发送成功
     */
    public boolean sendMusicUploadNotification(String musicTitle, String artist, int uploadUserId) {
        String message = String.format("音乐审核提醒\n标题: %s\n艺术家: %s\n上传用户ID: %d", 
            musicTitle, artist, uploadUserId);
        return sendNotification(message);
    }
}