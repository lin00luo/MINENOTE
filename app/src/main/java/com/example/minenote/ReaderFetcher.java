package com.example.minenote;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Network helper for RSS/Atom discovery and native-readable article extraction. */
public final class ReaderFetcher {
    private ReaderFetcher() {}

    public static final class ParsedArticle {
        public String guid, title, url, author, summary, content;
        public long publishedAt;
    }

    public static String normalizeHttpUrl(String raw) throws Exception {
        URI uri = new URI(raw.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("仅支持有效的 HTTP / HTTPS 地址");
        }
        return uri.normalize().toString();
    }

    public static String discoverFeed(String websiteUrl) throws Exception {
        String url = normalizeHttpUrl(websiteUrl);
        Document doc = Jsoup.connect(url).userAgent("MinenoteReader/1.0")
                .timeout(12000).followRedirects(true).get();
        Elements links = doc.select("link[rel~=(?i)alternate][type]");
        for (Element link : links) {
            String type = link.attr("type").toLowerCase();
            if (type.contains("rss") || type.contains("atom") || type.contains("xml")) {
                String href = link.absUrl("href");
                if (!href.isEmpty()) return normalizeHttpUrl(href);
            }
        }
        String host = URI.create(url).getScheme() + "://" + URI.create(url).getHost();
        String[] candidates = {"/feed", "/rss", "/feed.xml", "/atom.xml"};
        for (String candidate : candidates) {
            String probe = host + candidate;
            try {
                Document test = Jsoup.connect(probe).userAgent("MinenoteReader/1.0")
                        .ignoreContentType(true).timeout(5000).get();
                if (test.outerHtml().contains("<rss") || test.outerHtml().contains("<feed")) return probe;
            } catch (Exception ignored) { }
        }
        throw new IllegalArgumentException("没有找到 RSS / Atom 订阅源。可以直接粘贴 RSS 地址重试。");
    }

    public static List<ParsedArticle> fetchFeed(String feedUrl) throws Exception {
        String url = normalizeHttpUrl(feedUrl);
        Document doc = Jsoup.connect(url).userAgent("MinenoteReader/1.0")
                .ignoreContentType(true).timeout(15000).parser(Parser.xmlParser()).get();
        List<ParsedArticle> result = new ArrayList<>();
        Elements entries = doc.select("item, entry");
        for (Element entry : entries) {
            ParsedArticle a = new ParsedArticle();
            a.title = text(entry, "title");
            a.guid = text(entry, "guid");
            if (a.guid.isEmpty()) a.guid = text(entry, "id");
            Element link = entry.selectFirst("link[rel=alternate], link[href]");
            a.url = link == null ? text(entry, "link") : (link.hasAttr("href") ? link.absUrl("href") : link.text());
            if (a.url.isEmpty()) a.url = a.guid;
            try { a.url = normalizeHttpUrl(a.url); } catch (Exception badUrl) { continue; }
            if (a.guid.isEmpty()) a.guid = a.url;
            a.author = text(entry, "author name");
            if (a.author.isEmpty()) a.author = childText(entry, "dc:creator");
            a.summary = text(entry, "description");
            if (a.summary.isEmpty()) a.summary = text(entry, "summary");
            a.content = childText(entry, "content:encoded");
            if (a.content.isEmpty()) a.content = text(entry, "content");
            if (a.content.isEmpty()) a.content = a.summary;
            a.publishedAt = System.currentTimeMillis();
            if (!a.title.isEmpty()) result.add(a);
        }
        return result;
    }

    public static String extractArticle(String articleUrl, String fallback) {
        try {
            String url = normalizeHttpUrl(articleUrl);
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (compatible; MinenoteReader/1.0)")
                    .timeout(15000).followRedirects(true).get();
            doc.select("script,style,noscript,nav,footer,header,aside,form,iframe").remove();
            Element article = doc.selectFirst("article");
            if (article == null) article = doc.selectFirst("main");
            if (article == null) article = doc.body();
            String text = article == null ? "" : article.text();
            return text.length() >= 80 ? text : (fallback == null ? text : fallback);
        } catch (Exception e) {
            return fallback == null ? "" : fallback;
        }
    }

    private static String childText(Element parent, String tag) {
        for (Element child : parent.children()) {
            if (child.tagName().equalsIgnoreCase(tag)) return child.text().trim();
        }
        return "";
    }

    private static String text(Element parent, String selector) {
        Element e = parent.selectFirst(selector);
        return e == null ? "" : e.text().trim();
    }
}