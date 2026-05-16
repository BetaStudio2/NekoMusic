package com.neko.music.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.util.PublicMusicLookup.PublicMusic;

/** 音乐详情页 JSON-LD @graph（双语 inLanguage、面包屑、音频对象） */
public final class MusicDetailJsonLdBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();

    private MusicDetailJsonLdBuilder() {}

    public static String build(MusicDetailSeoContent c, PublicMusic music) {
        try {
            ArrayNode graph = JSON.createArrayNode();

            ObjectNode webSite = JSON.createObjectNode();
            webSite.put("@type", "WebSite");
            webSite.put("@id", c.siteBase + "/#website");
            webSite.put("name", MusicDetailSeoContent.SITE_NAME_ZH);
            webSite.put("alternateName", MusicDetailSeoContent.SITE_NAME_EN);
            webSite.put("url", c.siteBase + "/");
            webSite.set("inLanguage", langs());
            ObjectNode searchAction = webSite.putObject("potentialAction");
            searchAction.put("@type", "SearchAction");
            ObjectNode target = searchAction.putObject("target");
            target.put("@type", "EntryPoint");
            target.put("urlTemplate", c.siteBase + "/search/{search_term_string}");
            searchAction.put("query-input", "required name=search_term_string");
            graph.add(webSite);

            ObjectNode org = JSON.createObjectNode();
            org.put("@type", "Organization");
            org.put("@id", c.siteBase + "/#organization");
            org.put("name", MusicDetailSeoContent.SITE_NAME_ZH);
            org.put("alternateName", MusicDetailSeoContent.SITE_NAME_EN);
            org.put("url", c.siteBase + "/");
            graph.add(org);

            ObjectNode breadcrumb = JSON.createObjectNode();
            breadcrumb.put("@type", "BreadcrumbList");
            breadcrumb.put("@id", c.pageUrl + "#breadcrumb");
            ArrayNode bcItems = breadcrumb.putArray("itemListElement");
            bcItems.add(breadcrumbItem(1, c.siteBase + "/", MusicDetailSeoContent.SITE_NAME_ZH));
            bcItems.add(breadcrumbItem(2, c.searchUrl, c.title));
            bcItems.add(breadcrumbItem(3, c.pageUrl, c.title + " - " + c.artist));
            graph.add(breadcrumb);

            ObjectNode webPage = JSON.createObjectNode();
            webPage.put("@type", "WebPage");
            webPage.put("@id", c.pageUrl + "#webpage");
            webPage.put("url", c.pageUrl);
            webPage.put("name", c.pageTitle);
            webPage.put("description", c.metaDescription);
            webPage.set("inLanguage", langs());
            webPage.put("isPartOf", JSON.createObjectNode().put("@id", c.siteBase + "/#website"));
            webPage.put("primaryImageOfPage", c.coverUrl);
            webPage.put("breadcrumb", JSON.createObjectNode().put("@id", c.pageUrl + "#breadcrumb"));
            if (!c.updatedAt.isEmpty()) {
                webPage.put("dateModified", c.updatedAt);
            }
            graph.add(webPage);

            ObjectNode recording = JSON.createObjectNode();
            recording.put("@type", "MusicRecording");
            recording.put("@id", c.pageUrl + "#recording");
            recording.put("name", c.title);
            recording.put("url", c.pageUrl);
            recording.put("image", c.coverUrl);
            recording.put("description", c.metaDescription);
            recording.set("inLanguage", langs());
            if (music.duration > 0) {
                recording.put("duration", "PT" + music.duration + "S");
            }
            ObjectNode byArtist = recording.putObject("byArtist");
            byArtist.put("@type", "MusicGroup");
            byArtist.put("name", c.artist);
            if (!c.album.isEmpty()) {
                ObjectNode inAlbum = recording.putObject("inAlbum");
                inAlbum.put("@type", "MusicAlbum");
                inAlbum.put("name", c.album);
            }
            ObjectNode publisher = recording.putObject("publisher");
            publisher.put("@type", "Organization");
            publisher.put("name", MusicDetailSeoContent.SITE_NAME_ZH);
            publisher.put("url", c.siteBase + "/");
            ObjectNode listen = recording.putObject("potentialAction");
            listen.put("@type", "ListenAction");
            listen.put("target", c.pageUrl);
            graph.add(recording);

            ObjectNode audio = JSON.createObjectNode();
            audio.put("@type", "AudioObject");
            audio.put("@id", c.pageUrl + "#audio");
            audio.put("name", c.title);
            audio.put("description", c.ogDescription);
            audio.put("contentUrl", c.audioUrl);
            audio.put("encodingFormat", "audio/mpeg");
            audio.put("thumbnailUrl", c.coverUrl);
            if (music.duration > 0) {
                audio.put("duration", "PT" + music.duration + "S");
            }
            graph.add(audio);

            ObjectNode root = JSON.createObjectNode();
            root.put("@context", "https://schema.org");
            root.set("@graph", graph);
            return JSON.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static ArrayNode langs() {
        ArrayNode arr = JSON.createArrayNode();
        arr.add("zh-CN");
        arr.add("en");
        return arr;
    }

    private static ObjectNode breadcrumbItem(int position, String itemUrl, String name) {
        ObjectNode item = JSON.createObjectNode();
        item.put("@type", "ListItem");
        item.put("position", position);
        item.put("name", name);
        item.put("item", itemUrl);
        return item;
    }
}
