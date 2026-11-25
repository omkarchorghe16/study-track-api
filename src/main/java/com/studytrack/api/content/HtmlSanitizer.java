package com.studytrack.api.content;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans HTML from the editor (and later from imports) before it is stored. Keeps formatting, lists,
 * tables, code blocks and links; removes scripts, styles, event handlers and unknown tags; and keeps
 * images only when they point at StudyTrack's own media endpoint.
 */
@Component
public class HtmlSanitizer {
    private static final Pattern MEDIA_SRC = Pattern.compile("^/api/v1/media/(\\d{1,18})$");
    private static final String BASE_URI = "https://study-track.invalid";

    private final Safelist safelist = Safelist.relaxed()
            .addTags("s", "del", "ins", "hr", "mark", "figure", "figcaption")
            .addAttributes("pre", "class", "data-language", "spellcheck")
            .addAttributes("code", "class")
            .addAttributes("span", "class")
            .addAttributes("li", "data-list")
            .addAttributes("ol", "type")
            .addAttributes("img", "data-media-id")
            .removeProtocols("img", "src", "http", "https")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow")
            .preserveRelativeLinks(true);

    /** Returns safe HTML, or null when nothing visible remains. */
    public String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document dirty = Jsoup.parseBodyFragment(html, BASE_URI);
        Document clean = new Cleaner(safelist).clean(dirty);
        for (Element image : clean.select("img")) {
            String source = image.attr("src");
            if (!MEDIA_SRC.matcher(source).matches()) {
                image.remove();
            }
        }
        for (Element link : clean.select("a[href]")) {
            link.attr("target", "_blank");
        }
        clean.outputSettings().prettyPrint(false);
        String body = clean.body().html().strip();
        boolean hasContent = !clean.body().text().isBlank() || !clean.select("img, hr, pre").isEmpty();
        return hasContent ? body : null;
    }

    /** Ids of the StudyTrack images referenced by already sanitised HTML, in order of appearance. */
    public Set<Long> findMediaIds(String sanitizedHtml) {
        Set<Long> mediaIds = new LinkedHashSet<>();
        if (sanitizedHtml == null) {
            return mediaIds;
        }
        for (Element image : Jsoup.parseBodyFragment(sanitizedHtml).select("img[src]")) {
            Matcher matcher = MEDIA_SRC.matcher(image.attr("src"));
            if (matcher.matches()) {
                mediaIds.add(Long.parseLong(matcher.group(1)));
            }
        }
        return mediaIds;
    }
}
