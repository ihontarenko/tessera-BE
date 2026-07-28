package net.innoventa.tessera.dto.filter;

import java.util.List;

/**
 * The filter language's reference card as the editor's help panel receives it (ADR-0008).
 * <p>
 * Served rather than written into the client so the cheat-sheet is generated from the same constants
 * the evaluator reports errors against — a help panel that disagrees with the engine costs an author
 * more time than no help panel at all.
 */
public record FilterGrammarView(List<GrammarSection> sections) {

    /**
     * @param id    stable key for the client to style or order by
     * @param title the heading, in English; the client resolves {@code filter.help.<id>} over it
     */
    public record GrammarSection(String id, String title, List<GrammarEntry> entries) {
    }

    /**
     * @param syntax      what to type — an accessor, an operator, or a whole example expression
     * @param explanation what it means, in one line
     */
    public record GrammarEntry(String syntax, String explanation) {
    }

}
