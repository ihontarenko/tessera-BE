package net.innoventa.tessera.repository;

/**
 * One row of a {@code group by} — the key, and how many.
 *
 * <p>Exists so a bulk count comes back as something with names on it rather than as
 * {@code List<Object[]>}, which every caller would have to index into and none could be read without
 * counting columns. Constructed by JPQL {@code select new}, so the projection is checked when the query
 * is parsed rather than when somebody casts element zero.
 *
 * <p>⚠️ <strong>The key may be null.</strong> A grouping over a nullable column — an issue's resolution,
 * a transition's source status — has a null bucket that means something ("open", "the create
 * transition"), so callers collecting these into a map have to say what they want done with it rather
 * than hand it to {@code Collectors.toMap}, which throws.
 */
public record CountByKey(String key, long count) {
}
