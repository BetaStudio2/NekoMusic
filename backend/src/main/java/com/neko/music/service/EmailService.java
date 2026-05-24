package com.neko.music.service;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final SecureRandom secureRandom = new SecureRandom();
    private final ConfigManager configManager;
    private final String emailTemplate;
    private final String reviewTemplate;
    private final String reviewApprovedTemplate;
    private final String videoRenderCompleteTemplate;
    private final String neteaseLyricsValidationFailedTemplate;

    private static final int NETEASE_LYRICS_EMAIL_MAX_CHARS = 12_000;

    private static final ExecutorService ASYNC_MAIL_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "email-async-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    public EmailService(ConfigManager configManager) {
        this.configManager = configManager;
        this.emailTemplate = loadEmailTemplate();
        this.reviewTemplate = loadReviewTemplate();
        this.reviewApprovedTemplate = loadReviewApprovedTemplate();
        this.videoRenderCompleteTemplate = loadVideoRenderCompleteTemplate();
        this.neteaseLyricsValidationFailedTemplate = loadNeteaseLyricsValidationFailedTemplate();
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
     * 加载视频渲染完成邮件模板
     */
    private String loadVideoRenderCompleteTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("VideoRenderComplete.html")) {
            if (is == null) {
                logger.error("无法加载邮件模板 VideoRenderComplete.html");
                return getDefaultVideoRenderCompleteTemplate();
            }
            return new String(is.readAllBytes(), "UTF-8");
        } catch (IOException e) {
            logger.error("加载视频渲染完成邮件模板失败", e);
            return getDefaultVideoRenderCompleteTemplate();
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

    private String getDefaultVideoRenderCompleteTemplate() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>"
                + "<h2>分享视频已生成</h2>"
                + "<p>《{{musicName}}》 · {{artistName}}</p>"
                + "<p>时长约 {{durationSec}} 秒</p>"
                + "<p><a href=\"{{downloadUrl}}\">前往下载 MP4</a></p>"
                + "<p>生成时间：{{completedAt}}</p>"
                + "</body></html>";
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
     * 视频渲染完成通知（HTML 邮件，含下载页链接）。
     */
    public boolean sendVideoRenderCompleteEmail(String toEmail, String musicName, String artistName,
                                                double durationSec, String downloadUrl, boolean watermarked) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String completedAt = now.format(formatter);
        String durationText = String.format(java.util.Locale.ROOT, "%.0f", durationSec);

        String content = videoRenderCompleteTemplate
                .replace("{{musicName}}", escapeHtml(musicName))
                .replace("{{artistName}}", escapeHtml(artistName))
                .replace("{{durationSec}}", escapeHtml(durationText))
                .replace("{{downloadUrl}}", escapeHtml(downloadUrl))
                .replace("{{completedAt}}", escapeHtml(completedAt));
        if (watermarked) {
            content = content.replace("style=\"{{showWatermarkNote}}\"", "");
        } else {
            content = content.replace("style=\"{{showWatermarkNote}}\"", "style=\"display:none\"");
        }

        String subject = "NekoMusic - 分享视频已生成";
        return sendEmail(toEmail, subject, content);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String loadNeteaseLyricsValidationFailedTemplate() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("NeteaseLyricsValidationFailed.html")) {
            if (is == null) {
                logger.error("无法加载邮件模板 NeteaseLyricsValidationFailed.html");
                return getDefaultNeteaseLyricsValidationFailedTemplate();
            }
            return new String(is.readAllBytes(), "UTF-8");
        } catch (IOException e) {
            logger.error("加载网易云歌词校验失败邮件模板失败", e);
            return getDefaultNeteaseLyricsValidationFailedTemplate();
        }
    }

    private String getDefaultNeteaseLyricsValidationFailedTemplate() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>"
                + "<h2>网易云歌词 LRC 校验失败</h2>"
                + "<p>{{title}} — {{artist}} | 曲库 ID: {{musicId}} | 网易云 ID: {{songId}}</p>"
                + "<p><strong>原因：</strong>{{reason}}</p>"
                + "<pre>{{lyricsContent}}</pre>"
                + "<p>{{notifiedAt}}</p>"
                + "</body></html>";
    }

    /**
     * 入库完成后异步发送：网易云歌词 LRC 校验失败通知（单封邮件 BCC 群发所有管理员）。
     */
    public void scheduleNeteaseInvalidLyricsAlertToAdmins(
            long neteaseSongId,
            int ingestedMusicId,
            String title,
            String artist,
            String rawLyrics,
            String validationReason
    ) {
        ASYNC_MAIL_EXECUTOR.execute(() -> {
            try {
                sendNeteaseInvalidLyricsAlertToAdmins(
                        neteaseSongId, ingestedMusicId, title, artist, rawLyrics, validationReason);
            } catch (Exception e) {
                logger.warn("异步发送网易云歌词校验失败邮件异常 neteaseSongId={} musicId={}: {}",
                        neteaseSongId, ingestedMusicId, e.getMessage(), e);
            }
        });
    }

    /**
     * 单封邮件 BCC 群发给数据库中所有已启用管理员。
     *
     * @return 是否发送成功
     */
    public boolean sendNeteaseInvalidLyricsAlertToAdmins(
            long neteaseSongId,
            int ingestedMusicId,
            String title,
            String artist,
            String rawLyrics,
            String validationReason
    ) {
        List<String> recipients = new ArrayList<>(new LinkedHashSet<>(Main.getAdminDatabaseManager().getActiveAdminEmails()));
        if (recipients.isEmpty()) {
            logger.warn("网易云歌词校验失败，但无可用管理员邮箱，跳过邮件通知 neteaseSongId={} musicId={}",
                    neteaseSongId, ingestedMusicId);
            return false;
        }

        String lyricsForMail = truncateForEmail(rawLyrics);
        String notifiedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String content = neteaseLyricsValidationFailedTemplate
                .replace("{{musicId}}", escapeHtml(String.valueOf(ingestedMusicId)))
                .replace("{{songId}}", escapeHtml(String.valueOf(neteaseSongId)))
                .replace("{{title}}", escapeHtml(nullToEmpty(title)))
                .replace("{{artist}}", escapeHtml(nullToEmpty(artist)))
                .replace("{{reason}}", escapeHtml(nullToEmpty(validationReason)))
                .replace("{{lyricsContent}}", escapeHtml(lyricsForMail))
                .replace("{{notifiedAt}}", escapeHtml(notifiedAt));

        String subject = "NekoMusic - 网易云补全歌词校验失败 · " + nullToEmpty(title);
        boolean ok = sendEmailBroadcast(recipients, subject, content);
        logger.info("网易云歌词校验失败邮件(BCC 群发): neteaseSongId={} musicId={} 收件人={} 成功={}",
                neteaseSongId, ingestedMusicId, recipients.size(), ok);
        return ok;
    }

    /**
     * 一封邮件、BCC 抄送所有收件人（发件人地址作为唯一 To，兼容多数 SMTP）。
     */
    private boolean sendEmailBroadcast(List<String> bccRecipients, String subject, String content) {
        if (bccRecipients == null || bccRecipients.isEmpty()) {
            return false;
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", configManager.getSmtpHost());
            props.put("mail.smtp.port", String.valueOf(configManager.getSmtpPort()));
            props.put("mail.smtp.auth", "true");
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

            String from = configManager.getSmtpUsername();
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(from));
            InternetAddress[] bcc = new InternetAddress[bccRecipients.size()];
            for (int i = 0; i < bccRecipients.size(); i++) {
                bcc[i] = new InternetAddress(bccRecipients.get(i).trim());
            }
            message.setRecipients(Message.RecipientType.BCC, bcc);
            message.setSubject(subject);
            message.setContent(content, "text/html; charset=utf-8");

            Transport.send(message);
            logger.info("群发邮件已发送: BCC {} 人, subject={}", bccRecipients.size(), subject);
            return true;
        } catch (Exception e) {
            logger.error("群发邮件失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncateForEmail(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.length() <= NETEASE_LYRICS_EMAIL_MAX_CHARS) {
            return raw;
        }
        return raw.substring(0, NETEASE_LYRICS_EMAIL_MAX_CHARS)
                + "\n\n…（内容过长，已截断，完整原文见服务端日志）";
    }

    /**
     * 发送审核拒绝邮件
     */
    public boolean sendReviewRejectedEmail(String toEmail, String musicName, String artistName, String rejectReason) {
        // 使用缓存的模板
        
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
        // 使用缓存的模板
        
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
            logger.info("邮件已发送至: {}", to);
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
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
}