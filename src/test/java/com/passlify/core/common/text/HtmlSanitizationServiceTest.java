package com.passlify.core.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlSanitizationServiceTest {

    private final HtmlSanitizationService service = new HtmlSanitizationService();

    @Test
    void keepsAllowedFormatting() {
        String html = "<p>Two days of <b>software</b> and <em>AI</em>.</p><ul><li>Talks</li></ul>";
        String cleaned = service.sanitizeHtml(html);
        assertThat(cleaned).contains("<p>", "<b>software</b>", "<em>AI</em>", "<li>Talks</li>");
    }

    @Test
    void stripsScriptsAndUnsafeContent() {
        String html = "<p>hi</p><script>alert('x')</script><img src=x onerror=alert(1)>";
        String cleaned = service.sanitizeHtml(html);
        assertThat(cleaned).doesNotContainIgnoringCase("script")
                .doesNotContainIgnoringCase("onerror")
                .doesNotContain("<img");
    }

    @Test
    void enforcesSafeLinkAttributesAndDropsJavascriptProtocol() {
        String cleaned = service.sanitizeHtml(
                "<a href=\"javascript:alert(1)\">x</a><a href=\"https://ok.rs\">ok</a>");
        assertThat(cleaned).doesNotContain("javascript:");
        assertThat(cleaned).contains("https://ok.rs")
                .contains("rel=\"nofollow noopener noreferrer\"");
    }

    @Test
    void derivesPlainTextAndTreatsBlankAsNull() {
        assertThat(service.toPlainText("<p>Hello <b>world</b></p>")).isEqualTo("Hello world");
        assertThat(service.sanitizeHtml("   ")).isNull();
        assertThat(service.sanitizeHtml(null)).isNull();
        assertThat(service.toPlainText("<p></p>")).isNull();
    }
}
