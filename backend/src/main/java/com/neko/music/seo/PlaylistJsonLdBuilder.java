package com.neko.music.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.util.PublicPlaylistLookup.Track;

/** 歌单页 JSON-LD @graph（WebSite / 面包屑 / WebPage / MusicPlaylist + 曲目 ItemList）。 */
public final class PlaylistJsonLdBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 结构化数据里收录的曲目上限，避免超大歌单生成过大的 JSON-LD。 */
    private static final int MAX_TRACKS_IN_JSONLD = 100;

    private PlaylistJsonLdBuilder() {}

    public static String build(PlaylistSeoContent c) {
        try {
            ArrayNode graph = JSON.createArrayNode();

            ObjectNode webSite = JSON.createObjectNode();
            webSite.put("@type", "WebSite");
            webSite.put("@id", c.siteBase + "/#website");
            webSite.put("name", PlaylistSeoContent.SITE_NAME_ZH);
            webSite.put("alternateName", PlaylistSeoContent.SITE_NAME_EN);
            webSite.put("url", c.siteBase + "/");
            webSite.set("inLanguage", langs());
            ObjectNode searchAction = webSite.putObject("potentialAction");
            searchAction.put("@type", "SearchAction");
            ObjectNode target = searchAction.putObject("target");
            target.put("@type", "EntryPoint");
            target.put("urlTemplate", c.siteBase + "/search?q={search_term_string}");
            searchAction.put("query-input", "required name=search_term_string");
            graph.add(webSite);

            ObjectNode org = JSON.createObjectNode();
            org.put("@type", "Organization");
            org.put("@id", c.siteBase + "/#organization");
            org.put("name", PlaylistSeoContent.SITE_NAME_ZH);
            org.put("alternateName", PlaylistSeoContent.SITE_NAME_EN);
            org.put("url", c.siteBase + "/");
            graph.add(org);

            ObjectNode breadcrumb = JSON.createObjectNode();
            breadcrumb.put("@type", "BreadcrumbList");
            breadcrumb.put("@id", c.pageUrl + "#breadcrumb");
            ArrayNode bcItems = breadcrumb.putArray("itemListElement");
            bcItems.add(breadcrumbItem(1, c.siteBase + "/", "首页 Home"));
            bcItems.add(breadcrumbItem(2, c.searchUrl, "搜索 Search"));
            bcItems.add(breadcrumbItem(3, c.pageUrl, c.name));
            graph.add(breadcrumb);

            ObjectNode webPage = JSON.createObjectNode();
            webPage.put("@type", "WebPage");
            webPage.put("@id", c.pageUrl + "#webpage");
            webPage.put("url", c.pageUrl);
            webPage.put("name", c.pageTitle);
            webPage.put("description", c.metaDescription);
            webPage.set("inLanguage", langs());
            webPage.put("isPartOf", ref(c.siteBase + "/#website"));
            webPage.put("primaryImageOfPage", c.coverUrl);
            webPage.put("breadcrumb", ref(c.pageUrl + "#breadcrumb"));
            if (!c.updatedAt.isEmpty()) {
                webPage.put("dateModified", c.updatedAt);
            }
            graph.add(webPage);

            ObjectNode playlist = JSON.createObjectNode();
            playlist.put("@type", "MusicPlaylist");
            playlist.put("@id", c.pageUrl + "#playlist");
            playlist.put("name", c.name);
            playlist.put("url", c.pageUrl);
            playlist.put("image", c.coverUrl);
            if (!c.description.isEmpty()) {
                playlist.put("description", c.description);
            }
            if (c.musicCount > 0) {
                playlist.put("numTracks", c.musicCount);
            }
            playlist.set("inLanguage", langs());
            ObjectNode creator = playlist.putObject("creator");
            creator.put("@type", "Person");
            creator.put("name", c.creator);
            ObjectNode publisher = playlist.putObject("publisher");
            publisher.put("@type", "Organization");
            publisher.put("name", PlaylistSeoContent.SITE_NAME_ZH);
            publisher.put("url", c.siteBase + "/");
            if (!c.tracks.isEmpty()) {
                playlist.set("track", trackItemList(c));
            }
            graph.add(playlist);

            ObjectNode root = JSON.createObjectNode();
            root.put("@context", "https://schema.org");
            root.set("@graph", graph);
            return JsonLd.scriptSafe(JSON.writeValueAsString(root));
        } catch (Exception e) {
            return "{}";
        }
    }

    private static ObjectNode trackItemList(PlaylistSeoContent c) {
        ObjectNode list = JSON.createObjectNode();
        list.put("@type", "ItemList");
        list.put("numberOfItems", c.tracks.size());
        ArrayNode elements = list.putArray("itemListElement");
        int limit = Math.min(MAX_TRACKS_IN_JSONLD, c.tracks.size());
        for (int i = 0; i < limit; i++) {
            Track t = c.tracks.get(i);
            String detailUrl = c.siteBase + "/detail/" + t.id;
            ObjectNode recording = JSON.createObjectNode();
            recording.put("@type", "MusicRecording");
            recording.put("name", t.title == null ? "" : t.title);
            recording.put("url", detailUrl);
            if (t.artist != null && !t.artist.isBlank()) {
                ObjectNode byArtist = recording.putObject("byArtist");
                byArtist.put("@type", "MusicGroup");
                byArtist.put("name", t.artist);
            }
            if (t.duration > 0) {
                recording.put("duration", "PT" + t.duration + "S");
            }
            ObjectNode item = JSON.createObjectNode();
            item.put("@type", "ListItem");
            item.put("position", i + 1);
            item.put("url", detailUrl);
            item.set("item", recording);
            elements.add(item);
        }
        return list;
    }

    private static ArrayNode langs() {
        ArrayNode arr = JSON.createArrayNode();
        arr.add("zh-CN");
        arr.add("en");
        return arr;
    }

    private static ObjectNode ref(String id) {
        return JSON.createObjectNode().put("@id", id);
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
