package net.innoventa.tessera.repository;

import java.time.LocalDateTime;

/**
 * When an issue last changed status — the moment its current sit began.
 *
 * <p>⚠️ <strong>An issue with no row here has never moved</strong>, which is a real answer rather than a
 * gap: it has been sitting in the status it was raised in, and its age runs from {@code createdAt}.
 * Treating the missing row as "unknown" would drop exactly the issues that have been still the longest.
 */
public record LastStatusChange(String issueId, LocalDateTime at) {
}
