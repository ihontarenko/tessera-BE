package net.innoventa.tessera.service;

import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The sole owner of LexoRank string generation (ADR-0006) — nothing else computes ranks. A rank is a
 * lexicographically-ordered base-36 string; ordering issues is a plain {@code ORDER BY lexo_rank} and
 * moving one is a single-row update to a string strictly between its two new neighbours, never a mass
 * reindex.
 *
 * <h2>Ranks are a fixed-width number space, not an open bisection</h2>
 *
 * <p>Right-padding a rank with {@code '0'} is the same as multiplying it by the base, so lexicographic
 * order over strings <em>is</em> numeric order over the values those strings carry once both are padded
 * to a common width. Everything here is built on that one fact: a rank is read as a base-36 integer at
 * some width, arithmetic happens on the integer, and the answer is rendered back at that same width.
 *
 * <p>Two consequences worth stating, because both were wrong before:
 *
 * <ul>
 *   <li><strong>Appending is a step, not a midpoint.</strong> {@link #rankAfter(String)} adds
 *       {@link #STEP} to the last rank, so the string keeps its width. Bisecting towards the open end
 *       of the space instead — {@code between(last, null)} — converges on {@code "zzz…"} and then grows
 *       by one character every six appends, which is how a project's ranks reached the column's ceiling
 *       and stopped it accepting issues (TSSR-155).</li>
 *   <li><strong>Only a genuine insertion may lengthen a rank</strong>, and only when the gap between
 *       two neighbours is truly used up. {@link #WIDTH} digits and a step of {@link #STEP} leave room
 *       for a hundred million appends and thousands of insertions between any pair.</li>
 * </ul>
 *
 * <p>Growth is therefore bounded but not impossible, so a rank longer than
 * {@link #MAXIMUM_HEALTHY_LENGTH} is the signal that a project should be rebalanced —
 * {@code RankRebalanceService} acts on it and {@link #rebalancedRanks(int)} produces the fresh set.
 * The value {@code 0} ({@code "000000"}) is deliberately never handed out: it is the floor of the
 * space, and reserving it means there is always room below every rank in use.
 */
@Service
public class RankService {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

    private static final BigInteger BASE = BigInteger.valueOf(ALPHABET.length());
    private static final BigInteger TWO  = BigInteger.valueOf(2);

    /** The width every rank is generated at. 36^6 is 2.2 billion positions. */
    private static final int WIDTH = 6;

    /** The gap left between two consecutive appended ranks, so a drop between them needs no widening. */
    private static final BigInteger STEP = BigInteger.valueOf(8);

    /**
     * The rank length past which a project is rebalanced before anything else is written. Well above
     * the {@link #WIDTH} everything is generated at, and far below what the column can hold, so it is
     * reached only by real insertion pressure — or by data written before appending was fixed.
     */
    public static final int MAXIMUM_HEALTHY_LENGTH = 16;

    /** The rank of the first issue in an empty project — the middle of the whole space. */
    public String initialRank() {
        return render(BASE.pow(WIDTH).divide(TWO), WIDTH);
    }

    /**
     * A rank ordered strictly after {@code last} — appending an issue to the end of the list. One step
     * along, at the same width, so a project's ranks do not lengthen as it fills up. Only a project
     * that has genuinely run out of room at the end falls back to bisection, which widens by a digit
     * and thereby asks for a rebalance.
     */
    public String rankAfter(String last) {
        if (isBlank(last)) {
            return initialRank();
        }

        int width = widthFor(last, null);
        BigInteger next = valueOf(last, width).add(STEP);

        return next.compareTo(BASE.pow(width)) < 0 ? render(next, width) : between(last, null);
    }

    /**
     * A rank ordered strictly before {@code first} — prepending an issue to the front of the list. The
     * mirror of {@link #rankAfter(String)}: one step back at the same width, bisecting only once the
     * head of the space is used up.
     */
    public String rankBefore(String first) {
        if (isBlank(first)) {
            return initialRank();
        }

        int width = widthFor(null, first);
        BigInteger previous = valueOf(first, width).subtract(STEP);

        return previous.signum() > 0 ? render(previous, width) : between(null, first);
    }

    /**
     * A rank strictly between {@code lower} and {@code upper}. A null (or empty) {@code lower} is the
     * open start of the space, a null (or empty) {@code upper} its open end. Callers must pass
     * {@code lower < upper}.
     *
     * <p>The midpoint is taken at the widest of the two bounds. Where the two are already adjacent
     * there is no midpoint to take, so the width grows by a digit — which multiplies the gap by the
     * base — and the midpoint is taken again. That is the only place a rank gets longer.
     */
    public String between(String lower, String upper) {
        if (!isBlank(lower) && !isBlank(upper) && lower.compareTo(upper) >= 0) {
            throw new IllegalArgumentException("Lower rank must be strictly less than upper: '" + lower + "' >= '" + upper + "'");
        }

        int width = widthFor(lower, upper);

        while (true) {
            BigInteger floor   = isBlank(lower) ? BigInteger.ZERO : valueOf(lower, width);
            BigInteger ceiling = isBlank(upper) ? BASE.pow(width) : valueOf(upper, width);
            BigInteger middle  = floor.add(ceiling).divide(TWO);

            if (middle.compareTo(floor) > 0 && middle.compareTo(ceiling) < 0) {
                return render(middle, width);
            }

            width++;
        }
    }

    /**
     * {@code count} evenly-spaced, strictly-increasing ranks across the whole space — the fresh set a
     * rebalance writes over a project whose ranks have grown long. Fixed width, so the spacing is
     * uniform, every rank sorts correctly, and the gaps left between neighbours are as large as the
     * space allows.
     */
    public List<String> rebalancedRanks(int count) {
        if (count <= 0) {
            return List.of();
        }

        BigInteger positions = BigInteger.valueOf(count + 1L);

        int width = WIDTH;
        while (BASE.pow(width).compareTo(positions) <= 0) {
            width++;
        }

        BigInteger step = BASE.pow(width).divide(positions);

        List<String> ranks = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            ranks.add(render(step.multiply(BigInteger.valueOf(index)), width));
        }

        return ranks;
    }

    /** The width to do the arithmetic at: the canonical one, unless a bound is already wider. */
    private int widthFor(String lower, String upper) {
        int width = WIDTH;

        if (!isBlank(lower)) {
            width = Math.max(width, lower.length());
        }
        if (!isBlank(upper)) {
            width = Math.max(width, upper.length());
        }

        return width;
    }

    /** The value a rank carries once right-padded with {@code '0'} to {@code width} digits. */
    private BigInteger valueOf(String rank, int width) {
        BigInteger value = BigInteger.ZERO;

        for (int position = 0; position < width; position++) {
            int digit = position < rank.length() ? digitValueOf(rank.charAt(position)) : 0;
            value = value.multiply(BASE).add(BigInteger.valueOf(digit));
        }

        return value;
    }

    /** A value as a base-36 string of exactly {@code width} digits. */
    private String render(BigInteger value, int width) {
        StringBuilder builder = new StringBuilder(value.toString(ALPHABET.length()));

        while (builder.length() < width) {
            builder.insert(0, '0');
        }

        return builder.toString();
    }

    private int digitValueOf(char digit) {
        int value = ALPHABET.indexOf(Character.toLowerCase(digit));

        if (value < 0) {
            throw new IllegalArgumentException("Not a rank digit: '" + digit + "'");
        }

        return value;
    }

    private boolean isBlank(String rank) {
        return rank == null || rank.isEmpty();
    }

}
