package net.innoventa.tessera.service.filter;

import net.innoventa.tessera.domain.LinkTypeEffect;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.filter.IssueFilterView.EpicReference;
import net.innoventa.tessera.service.filter.IssueFilterView.LinkReference;
import net.innoventa.tessera.service.filter.IssueFilterView.MemberReference;
import net.innoventa.tessera.service.filter.IssueFilterView.PriorityReference;
import net.innoventa.tessera.service.filter.IssueFilterView.ResolutionReference;
import net.innoventa.tessera.service.filter.IssueFilterView.StatusReference;
import net.innoventa.tessera.service.filter.IssueFilterView.TypeReference;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * One four-card board, shared by every filter test, shaped so each built-in filter selects a different
 * and non-empty subset — a fixture where two filters agree by accident cannot tell them apart.
 * <p>
 * Everything is measured against {@link #NOW} rather than the clock, so the age-based filters
 * ("recently updated", "stale") mean the same thing whenever the suite runs.
 */
final class BoardFixture {

    static final Instant NOW = LocalDateTime.of(2026, 7, 28, 12, 0).toInstant(ZoneOffset.UTC);

    static final Member CALLER = Member.builder().id("member-1").displayName("Ivan").build();

    /**
     * Mine, open, in progress, a Highest bug, touched yesterday.
     * <p>
     * Its <em>single</em> label is load-bearing: {@code FilterGrammarReferenceTest} proves the
     * documented {@code 'x' in list} pitfall against exactly this card, and that pitfall only bites a
     * one-element list. Adding a second label here would leave the warning documented but unproven.
     */
    static final IssueFilterView MINE = new IssueFilterView(
        "issue-1", "TIC-1", "Fix the Login page",
        new TypeReference("Bug", 0),
        new PriorityReference("Highest"),
        new StatusReference("In Progress", "IN_PROGRESS"),
        null,
        member("member-1", "Ivan"),
        member("member-9", "Olena"),
        new EpicReference("TIC-42"),
        List.of("api"),
        List.of(),
        NOW.minusSeconds(days(30)), NOW.minusSeconds(days(1)), null
    );

    /** Someone else's, open, in progress, and blocked by {@link #MINE}. */
    static final IssueFilterView THEIRS = new IssueFilterView(
        "issue-2", "TIC-2", "Rework the audit trail",
        new TypeReference("Task", 0),
        new PriorityReference("Medium"),
        new StatusReference("In Progress", "IN_PROGRESS"),
        null,
        member("member-2", "Olena"),
        member("member-9", "Olena"),
        new EpicReference("TIC-42"),
        List.of(),
        List.of(new LinkReference("Blocks", LinkReference.INWARD, "TIC-1", LinkTypeEffect.BLOCKS_START.name())),
        NOW.minusSeconds(days(40)), NOW.minusSeconds(days(1)), null
    );

    /** Nobody's, open, a High bug, untouched for a month — the stale one. */
    static final IssueFilterView UNASSIGNED = new IssueFilterView(
        "issue-3", "TIC-3", "Flaky checkout test",
        new TypeReference("Bug", 0),
        new PriorityReference("High"),
        new StatusReference("To Do", "TO_DO"),
        null,
        null,
        member("member-1", "Ivan"),
        null,
        List.of("regression", "flaky"),
        List.of(),
        NOW.minusSeconds(days(60)), NOW.minusSeconds(days(30)), null
    );

    /** Mine, but finished — the card every "open" filter must drop. */
    static final IssueFilterView RESOLVED = new IssueFilterView(
        "issue-4", "TIC-4", "Ship the release notes",
        new TypeReference("Task", 0),
        new PriorityReference("Medium"),
        new StatusReference("Done", "DONE"),
        new ResolutionReference("Done"),
        member("member-1", "Ivan"),
        member("member-9", "Olena"),
        null,
        List.of(),
        List.of(new LinkReference("Blocks", LinkReference.OUTWARD, "TIC-2", LinkTypeEffect.BLOCKS_START.name())),
        NOW.minusSeconds(days(50)), NOW.minusSeconds(days(20)), NOW.minusSeconds(days(20))
    );

    static final List<IssueFilterView> BOARD = List.of(MINE, THEIRS, UNASSIGNED, RESOLVED);

    /**
     * The same four cards as the retired client-side stub saw them — a {@code BoardCard} projection,
     * written out by hand rather than derived from the views above.
     * <p>
     * Deriving them would defeat the purpose: these exist so the jME filters can be checked against the
     * behaviour members actually had, and a projection computed from the very fields the jME side reads
     * would turn that comparison into a tautology. {@code open} is the server's projection of
     * {@code resolution IS NULL} (ADR-0004), which is the field the stub read.
     */
    static List<StubCard> stubCards() {
        return List.of(
            new StubCard("issue-1", "member-1", true),
            new StubCard("issue-2", "member-2", true),
            new StubCard("issue-3", null, true),
            new StubCard("issue-4", "member-1", false)
        );
    }

    /** The three fields the retired stub filtered on, in the shape {@code BoardCard} delivered them. */
    record StubCard(String id, String assigneeId, boolean open) {
    }

    private static MemberReference member(String id, String displayName) {
        return new MemberReference(id, displayName);
    }

    private static long days(int count) {
        return count * 24L * 60L * 60L;
    }

    private BoardFixture() {
    }

}
