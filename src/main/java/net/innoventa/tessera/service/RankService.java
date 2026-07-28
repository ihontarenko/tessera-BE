package net.innoventa.tessera.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The sole owner of LexoRank string generation (ADR-0006) — nothing else computes ranks. A rank is a
 * lexicographically-ordered base-36 string; ordering issues is a plain {@code ORDER BY lexo_rank} and
 * moving one is a single-row update to a string strictly between its two new neighbours, never a mass
 * reindex.
 * <p>
 * The core is {@link #between(String, String)}: a recursive midpoint over the digit alphabet that
 * returns a string strictly between {@code lower} and {@code upper} (either bound may be open). Append,
 * prepend and the first-ever rank are all expressed through it. {@link #rebalancedRanks(int)} produces
 * an evenly-spaced fresh set for the rare case the space between a pair is exhausted.
 */
@Service
public class RankService {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    /** The rank of the first issue in an empty project — the middle of the whole space. */
    public String initialRank() {
        return between(null, null);
    }

    /** A rank ordered strictly after {@code last} (appending an issue to the end of the list). */
    public String rankAfter(String last) {
        return between(last, null);
    }

    /** A rank ordered strictly before {@code first} (prepending an issue to the front of the list). */
    public String rankBefore(String first) {
        return between(null, first);
    }

    /**
     * A rank strictly between {@code lower} and {@code upper}. A null (or empty) {@code lower} is the
     * open start of the space, a null (or empty) {@code upper} its open end. Callers must pass
     * {@code lower < upper}; the space between any two neighbours is effectively inexhaustible.
     */
    public String between(String lower, String upper) {
        String from = lower == null ? "" : lower;
        String to = (upper == null || upper.isEmpty()) ? null : upper;

        if (to != null && from.compareTo(to) >= 0) {
            throw new IllegalArgumentException("Lower rank must be strictly less than upper: '" + from + "' >= '" + to + "'");
        }

        return midpoint(from, to);
    }

    /**
     * {@code count} evenly-spaced, strictly-increasing ranks across the whole space — a fresh set for
     * rebalancing a project whose ranks have drifted or exhausted the space between a pair. Fixed-width
     * base-36 so the spacing is uniform and every rank sorts correctly.
     */
    public List<String> rebalancedRanks(int count) {
        if (count <= 0) {
            return List.of();
        }

        int width = Math.max(2, Integer.toString(count + 1, BASE).length() + 1);
        long span = (long) Math.pow(BASE, width);
        long step = span / (count + 1);

        List<String> ranks = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            ranks.add(toFixedWidth(step * index, width));
        }

        return ranks;
    }

    /** Recursive midpoint between two digit strings; {@code upper} null means the open end of the space. */
    private String midpoint(String lower, String upper) {
        if (upper != null) {
            int shared = sharedPrefixLength(lower, upper);
            if (shared > 0) {
                String lowerRemainder = lower.length() > shared ? lower.substring(shared) : "";
                return upper.substring(0, shared) + midpoint(lowerRemainder, upper.substring(shared));
            }
        }

        int lowerDigit = lower.isEmpty() ? 0 : valueOf(lower.charAt(0));
        int upperDigit = (upper == null || upper.isEmpty()) ? BASE : valueOf(upper.charAt(0));

        if (upperDigit - lowerDigit > 1) {
            int mid = (lowerDigit + upperDigit) / 2;
            return String.valueOf(digitOf(mid));
        }

        // Adjacent digits: keep the lower digit and go one level deeper, where there is room again.
        if (upper != null && upper.length() > 1) {
            return upper.substring(0, 1);
        }

        String lowerRemainder = lower.isEmpty() ? "" : lower.substring(1);
        return digitOf(lowerDigit) + midpoint(lowerRemainder, null);
    }

    private int sharedPrefixLength(String lower, String upper) {
        int position = 0;
        while (position < upper.length()) {
            char lowerChar = position < lower.length() ? lower.charAt(position) : '0';
            if (lowerChar != upper.charAt(position)) {
                break;
            }
            position++;
        }
        return position;
    }

    private String toFixedWidth(long value, int width) {
        StringBuilder builder = new StringBuilder(Long.toString(value, BASE));
        while (builder.length() < width) {
            builder.insert(0, '0');
        }
        return builder.toString();
    }

    private int valueOf(char digit) {
        int value = ALPHABET.indexOf(Character.toLowerCase(digit));
        if (value < 0) {
            throw new IllegalArgumentException("Not a rank digit: '" + digit + "'");
        }
        return value;
    }

    private char digitOf(int value) {
        return ALPHABET.charAt(value);
    }

}
