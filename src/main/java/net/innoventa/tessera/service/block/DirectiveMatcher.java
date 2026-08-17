package net.innoventa.tessera.service.block;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises directive lines inside a document's Markdown — the server-side twin of the reader's
 * scanner.
 *
 * <p>⚠️ <strong>This class is the whole security argument for the resolve endpoint.</strong> Without it,
 * "resolve these directives for page X" is a way to read any issue in any project by naming it: the
 * caller supplies the directive, so the caller chooses what gets looked up. Checking that the exact line
 * appears in the page's <em>stored</em> text means the endpoint can only ever answer what the document
 * already says — the document becomes the allowlist.
 *
 * <p>⚠️ <strong>Which is also why the argument is compared as written.</strong> The engine passes what
 * the client sent, untouched, and compares it against the stored line. Normalising either side would
 * widen the check to a family of lines, and the family is exactly what an attacker gets to choose from.
 * Case and surrounding whitespace are forgiven because Markdown already treats them as the same line;
 * nothing else is.
 */
public final class DirectiveMatcher {

    /**
     * A directive line: {@code :::name argument}.
     *
     * <p>The name is letters only, matching what the reader's block parser claims. An argument is
     * required — {@code :::note} with nothing after it is a callout's opening fence in this product's
     * Markdown, not a data block.
     */
    private static final Pattern DIRECTIVE_LINE = Pattern.compile("^:::([a-zA-Z]+)[ \\t]+(.+?)[ \\t]*$");

    private DirectiveMatcher() {
    }

    /** True when a {@code :::name argument} line for this exact name and argument appears in the text. */
    public static boolean contains(String markdown, String name, String argument) {
        if (markdown == null || name == null || argument == null) {
            return false;
        }

        for (String line : markdown.split("\\R")) {
            Matcher matcher = DIRECTIVE_LINE.matcher(line.trim());

            if (matcher.matches()
                && matcher.group(1).equalsIgnoreCase(name)
                && matcher.group(2).trim().equalsIgnoreCase(argument.trim())) {
                return true;
            }
        }

        return false;
    }

    /** The directive name as the engine keys on it. */
    public static String normaliseName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

}
