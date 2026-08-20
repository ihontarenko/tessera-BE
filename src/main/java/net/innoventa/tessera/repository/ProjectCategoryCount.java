package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.StatusCategory;

/**
 * One project's issue count in one status category — the row a progress meter is built from.
 *
 * <p>Its own projection rather than {@link CountByKey} because the key is two things: a
 * {@code group by} over one column could not say <em>which project</em> and <em>which bucket</em> at
 * once, and a caller stitching a composite string key back apart is how a project identifier with a
 * separator in it becomes a bug.
 */
public record ProjectCategoryCount(String projectId, StatusCategory category, long count) {
}
