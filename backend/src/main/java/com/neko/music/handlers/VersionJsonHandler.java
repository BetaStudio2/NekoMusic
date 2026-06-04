package com.neko.music.handlers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.service.AppReleaseService;
import com.neko.music.util.SiteUrlResolver;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** 客户端版本检查 JSON（ver / pc_ver 均来自数据库 app_release） */
public class VersionJsonHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(VersionJsonHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AppReleaseService releaseService = Main.getAppReleaseService();
        Optional<AppReleaseService.AppRelease> release = releaseService.getRelease();
        if (release.isEmpty()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE_503);
            response.setContentType("application/json;charset=utf-8");
            ObjectNode err = Main.getObjectMapper().createObjectNode();
            err.put("success", false);
            err.put("message", "未配置客户端版本，请在 app_release 表写入 android_ver 与 pc_ver");
            response.getWriter().write(Main.getObjectMapper().writeValueAsString(err));
            return;
        }

        AppReleaseService.AppRelease r = release.get();
        String siteBase = trimTrailingSlash(SiteUrlResolver.resolvePublicSiteBase(request));

        ObjectNode root = Main.getObjectMapper().createObjectNode();
        root.put("ver", r.androidVer());
        root.put("updateUrl", siteBase + "/" + r.androidVer() + ".apk");

        ObjectNode pc = root.putObject("pc");
        String pcVer = r.pcVer();
        pc.put("pc_ver", pcVer);
        pc.put("windows", siteBase + "/Neko云音乐 Setup " + pcVer + ".exe");
        pc.put("linux", siteBase + "/NekoMusic_" + pcVer + "_amd64.deb");
        pc.put("mac", siteBase + "/Neko云音乐" + pcVer + ".dmg");

        response.setStatus(HttpStatus.OK_200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Cache-Control", "public, max-age=300");
        response.getWriter().write(Main.getObjectMapper().writeValueAsString(root));
        logger.debug("version.json ver={} pc_ver={}", r.androidVer(), pcVer);
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isEmpty()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
