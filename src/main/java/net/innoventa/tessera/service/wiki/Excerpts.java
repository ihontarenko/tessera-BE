package net.innoventa.tessera.service.wiki;

import net.innoventa.tessera.domain.WikiPage;

/**
 * The first readable sentences of a document, with the Markdown taken off.
 *
 * <p>⚠️ <strong>Not a renderer, and it must not become one.</strong> This exists so a list can say what
 * a page is about without shipping the whole document and without a Markdown pass per row. Its output
 * lands in a plain text node — a heading's hashes, a link's brackets and a fence's backticks are noise
 * there, so they are stripped rather than interpreted.
 *
 * <p>⚠️ <strong>Fenced code and front matter are skipped whole</strong>, because the top of a technical
 * page is very often a code block, and an excerpt reading {@code bash docker compose up -d} says nothing
 * about the page. What is wanted is the first prose.
 */
public final class Excerpts {

    private Excerpts() {
    }

    public static String of(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }

        StringBuilder prose = new StringBuilder();
        boolean insideFence = false;

        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```") || trimmed.startsWith(";;;") || trimmed.startsWith(":::")) {
                insideFence = !insideFence;
                continue;
            }

            if (insideFence || trimmed.isEmpty() || isRule(trimmed)) {
                continue;
            }

            prose.append(strip(trimmed)).append(' ');

            if (prose.length() >= WikiPage.MAXIMUM_EXCERPT_LENGTH) {
                break;
            }
        }

        String flattened = prose.toString().replaceAll("\\s+", " ").trim();

        if (flattened.isEmpty()) {
            return null;
        }

        return flattened.length() <= WikiPage.MAXIMUM_EXCERPT_LENGTH
            ? flattened
            : flattened.substring(0, WikiPage.MAXIMUM_EXCERPT_LENGTH).trim();
    }

    /** A horizontal rule or a table's separator row — punctuation carrying no words at all. */
    private static boolean isRule(String line) {
        return line.matches("[-=|:\\s]+");
    }

    /**
     * One line's Markdown decoration removed.
     *
     * <p>Link text is kept and its target dropped: {@code [the runbook](…)} reads as "the runbook", which
     * is what somebody scanning a list wants, whereas keeping the URL would fill the excerpt with
     * addresses.
     */
    private static String strip(String line) {
        return line
            .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
            .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
            .replaceAll("^\\s*[#>]+\\s*", "")
            .replaceAll("^\\s*[-*+]\\s+", "")
            .replaceAll("^\\s*\\d+\\.\\s+", "")
            .replaceAll("[*_`~]", "")
            .replaceAll("\\|", " ");
    }

}
