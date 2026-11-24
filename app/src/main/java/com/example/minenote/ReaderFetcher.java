package com.example.minenote;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
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
                String xml = test.outerHtml().toLowerCase(java.util.Locale.ROOT);
                if (xml.contains("<rss") || xml.contains("<feed")) return probe;
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
            a.url = link == null ? text(entry, "link")
                    : (link.hasAttr("href") ? link.absUrl("href") : link.text());
            if (a.url.isEmpty()) a.url = a.guid;
            try { a.url = normalizeHttpUrl(a.url); } catch (Exception badUrl) { continue; }
            if (a.guid.isEmpty()) a.guid = a.url;
            a.author = text(entry, "author name");
            if (a.author.isEmpty()) a.author = childText(entry, "dc:creator");
            a.summary = firstHtml(entry, "description", "summary");
            a.content = firstHtml(entry, "content:encoded", "content");
            if (a.content.isEmpty()) a.content = a.summary;
            a.publishedAt = System.currentTimeMillis();
            if (!a.title.isEmpty()) result.add(a);
        }
        return result;
    }

    /**
     * Returns sanitized article HTML, not flattened text. Keeping the markup here is
     * important: the native reader needs paragraph, heading, list and image boundaries.
     */
    public static String extractArticle(String articleUrl, String fallback) {
        try {
            String url = normalizeHttpUrl(articleUrl);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; MinenoteReader/1.0)")
                    .timeout(15000).followRedirects(true).get();
            Element article = findArticleContainer(doc);
            if (article != null && article.text().trim().length() >= 80) {
                return sanitizeArticleHtml(article, doc.baseUri());
            }
        } catch (Exception ignored) {
            // RSS content is still useful when the publisher blocks direct page fetching.
        }
        return cleanFallback(fallback);
    }

    private static Element findArticleContainer(Document doc) {
        doc.select("script,style,noscript,nav,footer,header,aside,form,iframe,button,svg").remove();
        String[] selectors = {
                "article", "[itemprop=articleBody]", "main",
                ".post-content", ".entry-content", ".article-content",
                ".article-body", ".post-body", "#article-content"
        };
        Element best = null;
        int bestScore = 0;
        for (String selector : selectors) {
            for (Element candidate : doc.select(selector)) {
                int textLength = candidate.text().trim().length();
                if (textLength < 80) continue;
                int paragraphs = candidate.select("p").size();
                int images = candidate.select("img").size();
                int score = textLength + paragraphs * 120 + images * 80;
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        if (best == null && doc.body() != null && doc.body().text().trim().length() >= 80) {
            best = doc.body();
        }
        return best;
    }

    private static String sanitizeArticleHtml(Element container, String baseUri) {
        Document fragment = Jsoup.parseBodyFragment(container.html(), baseUri);
        fragment.outputSettings().prettyPrint(false);
        fragment.select("script,style,noscript,iframe,form,button,nav,footer,header,aside,svg").remove();

        for (Element element : fragment.getAllElements()) {
            // Remove active content and presentation styles while preserving semantic tags.
            List<Node> attributesToRemove = new ArrayList<>();
            for (org.jsoup.nodes.Attribute attribute : element.attributes()) {
                String key = attribute.getKey().toLowerCase(java.util.Locale.ROOT);
                if (key.startsWith("on") || key.equals("style") || key.equals("srcdoc")) {
                    attributesToRemove.add(element);
                    break;
                }
            }
            if (!attributesToRemove.isEmpty()) {
                element.removeAttr("style");
                List<String> names = new ArrayList<>();
                for (org.jsoup.nodes.Attribute attribute : element.attributes()) {
                    String key = attribute.getKey().toLowerCase(java.util.Locale.ROOT);
                    if (key.startsWith("on") || key.equals("srcdoc")) names.add(attribute.getKey());
                }
                for (String name : names) element.removeAttr(name);
            }
            if (element.normalName().equals("img")) {
                if (!element.hasAttr("src") || element.attr("src").trim().isEmpty()) {
                    String lazy = element.hasAttr("data-src") ? element.attr("data-src")
                            : element.attr("data-original");
                    if (!lazy.isEmpty()) element.attr("src", lazy);
                }
                String imageUrl = element.absUrl("src");
                if (!imageUrl.isEmpty()) element.attr("src", imageUrl);
                element.removeAttr("srcset");
                element.attr("alt", element.attr("alt"));
            } else if (element.normalName().equals("a")) {
                String href = element.absUrl("href");
                if (!href.isEmpty()) element.attr("href", href);
                element.removeAttr("target");
            }
        }
        return fragment.body().html().trim();
    }

    /** Normalize RSS HTML, including feeds that double-escape their markup. */
    private static String cleanFallback(String html) {
        if (html == null || html.trim().isEmpty()) return "";
        Document fragment = Jsoup.parseBodyFragment(html);
        fragment.select("script,style,noscript,iframe").remove();

        String visible = fragment.body().text();
        if (visible.matches("(?is).*&lt;\\s*/?\\s*(p|div|h[1-6]|li|blockquote|img)\\b.*")) {
            fragment = Jsoup.parseBodyFragment(org.jsoup.parser.Parser.unescapeEntities(visible, false));
            fragment.select("script,style,noscript,iframe").remove();
        }
        return sanitizeArticleHtml(fragment.body(), "");
    }

    private static String firstHtml(Element parent, String... selectors) {
        for (String selector : selectors) {
            Element element = parent.selectFirst(selector);
            if (element == null) continue;
            String html = element.html().trim();
            if (!html.isEmpty()) return html;
            String value = element.text().trim();
            if (!value.isEmpty()) return "<p>" + org.jsoup.nodes.Entities.escape(value) + "</p>";
        }
        return "";
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