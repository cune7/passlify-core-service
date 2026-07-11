package com.passlify.core.common.text;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

/**
 * Server-side sanitization of organizer-supplied rich text (EVENT_DOMAIN_SPEC §17.3,
 * §38). We never trust the frontend editor: description HTML is re-sanitized here
 * against a fixed allowlist, and a plain-text projection is derived for search,
 * previews, and accessibility.
 *
 * <p>Allowed: paragraphs, line breaks, bold/italic/underline, ordered/unordered
 * lists, h2–h4 headings, blockquotes, and safe {@code https}/{@code mailto} links.
 * Everything else — scripts, iframes, styles, event handlers, unsafe protocols — is
 * stripped.
 */
@Service
public class HtmlSanitizationService {

    private static final Safelist DESCRIPTION_SAFELIST = buildDescriptionSafelist();

    private static Safelist buildDescriptionSafelist() {
        return new Safelist()
                .addTags("p", "br", "b", "strong", "i", "em", "u",
                        "ul", "ol", "li", "h2", "h3", "h4", "blockquote", "a")
                .addAttributes("a", "href", "title", "target", "rel")
                .addProtocols("a", "href", "https", "mailto")
                .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");
    }

    /**
     * @return sanitized HTML safe for public rendering, or {@code null} if the input
     *     is {@code null} or blank after sanitization.
     */
    public String sanitizeHtml(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return null;
        }
        String cleaned = Jsoup.clean(rawHtml, DESCRIPTION_SAFELIST);
        return cleaned.isBlank() ? null : cleaned;
    }

    /**
     * @return a plain-text projection of the (sanitized) HTML with block-level
     *     structure flattened to whitespace, or {@code null} if empty.
     */
    public String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document doc = Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        String text = doc.body().text().trim();
        return text.isBlank() ? null : text;
    }
}
