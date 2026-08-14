package net.innoventa.tessera.repository;

/**
 * How many issues of {@code childIssueTypeId} sit under a parent of {@code parentIssueTypeId}.
 *
 * <p>The whole shape of the hierarchy, as data rather than as rows: "parent must be strictly higher"
 * (ADR-0014) is a rule about two levels, so deciding whether a proposed level change breaks anything
 * needs the pairings and their sizes and nothing else. Loading the issues themselves to find out would
 * read the table to answer a question about the catalog.
 */
public record IssueTypePairCount(String parentIssueTypeId, String childIssueTypeId, long count) {
}
