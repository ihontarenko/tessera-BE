package net.innoventa.tessera.dto.configuration;

import java.util.List;

/**
 * What holds a configuration row, counted and named.
 *
 * <p>⚠️ <strong>One answer, two readers.</strong> The Administration screen shows this before offering
 * Delete, and the refusal carries the same object when Delete is pressed anyway. That is the whole
 * reason it is a value rather than two message-building methods: a warning and an enforcement that can
 * disagree is worse than either alone, because an administrator told "nothing holds this" and then
 * refused concludes the screen is lying.
 *
 * <p>The counts are what a person acts on; the names are what they act <em>with</em>. "12 issues in 3
 * projects" answers how bad it is, and "OPS, WEB, INT" answers where to go.
 *
 * @param holders every kind of thing that has this row, one entry per kind; a kind holding nothing is
 *                left out rather than reported as zero
 */
public record ConfigurationUsageReport(List<Holder> holders) {

    public boolean isEmpty() {
        return holders.isEmpty();
    }

    /**
     * The report as one sentence — "12 issues in 3 projects, 4 board columns".
     *
     * <p>Built here rather than at each throw site so the refusal and the panel above it are the same
     * words. A client wanting to render the parts separately reads {@link #holders}; the sentence is
     * what goes in the {@code ProblemDetail} for everything that only shows a message.
     */
    public String describe() {
        return String.join(", ", holders.stream().map(Holder::phrase).toList());
    }

    /**
     * One kind of holder.
     *
     * <p>{@code kind} and {@code phrase} are deliberately both here. The token is what a client branches
     * on — an icon, a link into the boards screen — and the phrase is what it prints; deriving the
     * second from the first would mean a noun table in two languages for six words, and deriving the
     * first from the second would mean parsing English.
     *
     * @param kind   a stable token: {@code issues}, {@code boardColumns}, {@code transitions},
     *               {@code schemes}, {@code projects}, {@code issueLinks}, {@code instanceDefault}
     * @param count  how many there are — the number, without the sentence around it
     * @param phrase the same fact in words, already pluralised and already compound where it needs to
     *               be ("12 issues in 3 projects")
     * @param names  the ones worth naming — a project key, a scheme name, a workflow
     */
    public record Holder(String kind, long count, String phrase, List<String> names) {

        public static Holder of(String kind, long count, String phrase) {
            return new Holder(kind, count, phrase, List.of());
        }
    }
}
