package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;

public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final ConfigManager configManager;
    private final String emailTemplate;

    public EmailService(ConfigManager configManager) {
        this.configManager = configManager;
        this.emailTemplate = loadEmailTemplate();
    }

    /**
     * 加载邮件模板
     */
    private String loadEmailTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("email.html")) {
            if (is == null) {
                logger.error("无法加载邮件模板 email.html");
                return getDefaultEmailTemplate();
            }
            return new String(is.readAllBytes(), "UTF-8");
        } catch (IOException e) {
            logger.error("加载邮件模板失败", e);
            return getDefaultEmailTemplate();
        }
    }

    /**
     * 加载审核邮件模板
     */
    private String loadReviewTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("Review.html")) {
            if (is == null) {
                logger.error("无法加载审核邮件模板 Review.html");
                return getDefaultReviewTemplate();
            }
            return new String(is.readAllBytes(), "UTF-8");
        } catch (IOException e) {
            logger.error("加载审核邮件模板失败", e);
            return getDefaultReviewTemplate();
        }
    }

    /**
     * 加载审核通过邮件模板
     */
    private String loadReviewApprovedTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("ReviewApproved.html")) {
            if (is == null) {
                logger.error("无法加载审核通过邮件模板 ReviewApproved.html");
                return getDefaultReviewApprovedTemplate();
            }
            return new String(is.readAllBytes(), "UTF-8");
        } catch (IOException e) {
            logger.error("加载审核通过邮件模板失败", e);
            return getDefaultReviewApprovedTemplate();
        }
    }

    /**
     * 默认审核邮件模板
     */
    private String getDefaultReviewTemplate() {
        return "<!DOCTYPE html>" +
               "<html><head><meta charset='UTF-8'><title>审核结果</title></head>" +
               "<body>" +
               "<h2>您好，</h2>" +
               "<p>您的音乐《{{musicName}}》审核未通过。</p>" +
               "<p>拒绝原因：{{rejectReason}}</p>" +
               "<p>审核时间：{{auditDate}}</p>" +
               "</body></html>";
    }

    /**
     * 默认审核通过邮件模板
     */
    private String getDefaultReviewApprovedTemplate() {
        return "<!DOCTYPE html>" +
               "<html><head><meta charset='UTF-8'><title>审核结果</title></head>" +
               "<body>" +
               "<h2>您好，</h2>" +
               "<p>恭喜！您的音乐《{{musicName}}》已通过审核。</p>" +
               "<p>您的音乐已添加到 NekoMusic 音乐库中。</p>" +
               "<p>审核时间：{{auditDate}}</p>" +
               "</body></html>";
    }

    /**
     * 默认邮件模板
     */
    private String getDefaultEmailTemplate() {
        return "<!DOCTYPE html>" +
               "<html><head><meta charset='UTF-8'><title>验证码</title></head>" +
               "<body>" +
               "<h2>您好，</h2>" +
               "<p>感谢您注册 NekoMusic！</p>" +
               "<p>您的验证码是：<strong>{{verificationCode}}</strong></p>" +
               "<p>此验证码将在5分钟内有效，请尽快使用。</p>" +
               "</body></html>";
    }

    /**
     * 发送验证码邮件
     */
    public boolean sendVerificationCode(String toEmail, String username, String verificationCode) {
        String subject = "NekoMusic - 验证码";
        String content = emailTemplate.replace("{{verificationCode}}", verificationCode);

        return sendEmail(toEmail, subject, content);
    }

    /**
     * 发送审核拒绝邮件
     */
    public boolean sendReviewRejectedEmail(String toEmail, String musicName, String artistName, String rejectReason) {
        String reviewTemplate = loadReviewTemplate();
        
        // 格式化日期
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String auditDate = now.format(formatter);
        
        // 替换模板变量
        String content = reviewTemplate
            .replace("{{musicName}}", musicName)
            .replace("{{artistName}}", artistName)
            .replace("{{rejectReason}}", rejectReason)
            .replace("{{auditDate}}", auditDate);
        
        // 如果有拒绝原因则显示，否则隐藏
        if (rejectReason == null || rejectReason.isEmpty()) {
            content = content.replace("style=\"{{showReason}}\"", "style=\"display:none\"");
        } else {
            content = content.replace("style=\"{{showReason}}\"", "");
        }

        String subject = "NekoMusic - 审核结果通知";
        return sendEmail(toEmail, subject, content);
    }

    /**
     * 发送审核通过邮件
     */
    public boolean sendReviewApprovedEmail(String toEmail, String musicName, String artistName) {
        String reviewTemplate = loadReviewApprovedTemplate();
        
        // 格式化日期
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String auditDate = now.format(formatter);
        
        // 替换模板变量
        String content = reviewTemplate
            .replace("{{musicName}}", musicName)
            .replace("{{artistName}}", artistName)
            .replace("{{auditDate}}", auditDate);

        String subject = "NekoMusic - 审核结果通知";
        return sendEmail(toEmail, subject, content);
    }

    /**
     * 发送邮件
     */
    private boolean sendEmail(String to, String subject, String content) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", configManager.getSmtpHost());
            props.put("mail.smtp.port", String.valueOf(configManager.getSmtpPort()));
            props.put("mail.smtp.auth", "true");

            // 根据配置设置SSL或TLS
            if (configManager.isSmtpSsl()) {
                props.put("mail.smtp.ssl.enable", "true");
            } else if (configManager.isSmtpTls()) {
                props.put("mail.smtp.starttls.enable", "true");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        configManager.getSmtpUsername(),
                        configManager.getSmtpPassword()
                    );
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(configManager.getSmtpUsername()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setContent(content, "text/html; charset=utf-8");

            Transport.send(message);
            logger.info("验证码邮件已发送至: {}", to);
            return true;
        } catch (Exception e) {
            logger.error("发送邮件失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 生成随机验证码
     */
    public String generateVerificationCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}