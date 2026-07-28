-- =============================================================================
--  V000010  Saved-filter presets — the universal filters the product ships
--
--  Universal SQL: H2 / MySQL / PostgreSQL compatible. Identical to the mysql copy
--  bar the utf8mb4 header.
--
--  These are GLOBAL rows: null project_id, null owner_member_id. That is the whole
--  point of a preset — "my open issues" means the same thing in every project and
--  belongs to no one, so binding it to a project would mean re-seeding it for every
--  project ever created, and binding it to a member would let that member rename
--  everyone's copy. Nobody owns them, so nobody can edit or delete them; they are
--  read-only for every caller (SavedFilterService).
--
--  Every predicate below is verified against the real jME engine by
--  SavedFilterPresetTest, which parses THIS FILE rather than a copy — a preset that
--  fails to parse or does not return a boolean fails the build, because a broken
--  seeded row would otherwise be a filter that is broken for every member of every
--  project, forever.
--
--  Two spellings here are load-bearing and must not be "tidied":
--    * `(… in […])` keeps its brackets — jME binds `in` LOOSER than `and`, so the
--      unbracketed form parses as `name in ([…] and …)` and quietly answers false.
--    * timestamps compare through `now | minusDays(n)`, which needs an Instant;
--      IssueFilterView carries Instants for exactly this reason.
--
--  One preset per line: the test tokenises this file row by row.
-- =============================================================================

INSERT INTO saved_filters (id, project_id, owner_member_id, name, description, expression, visibility, created_at, updated_at) VALUES
('preset-my-open-issues',     NULL, NULL, 'My open issues',      'Assigned to me and not yet resolved',              'issue.assignee == currentMember and issue.resolution is null',                          'GLOBAL', '2026-07-28 00:00:00', '2026-07-28 00:00:00'),
('preset-reported-by-me',     NULL, NULL, 'Reported by me',      'Raised by me and still open',                      'issue.reporter == currentMember and issue.resolution is null',                          'GLOBAL', '2026-07-28 00:00:00', '2026-07-28 00:00:00'),
('preset-unassigned-open',    NULL, NULL, 'Unassigned open work','Open and waiting for someone to pick it up',       'issue.assignee is null and issue.resolution is null',                                   'GLOBAL', '2026-07-28 00:00:00', '2026-07-28 00:00:00'),
('preset-high-priority-open', NULL, NULL, 'High priority open',  'Highest and High priority work still open',        '(issue.priority.name in [''Highest'',''High'']) and issue.resolution is null',           'GLOBAL', '2026-07-28 00:00:00', '2026-07-28 00:00:00'),
('preset-open-bugs',          NULL, NULL, 'Open bugs',           'Bugs that have not been resolved',                 'issue.type.name == ''Bug'' and issue.resolution is null',                               'GLOBAL', '2026-07-28 00:00:00', '2026-07-28 00:00:00'),
('preset-blocked-work',       NULL, NULL, 'Blocked work',        'Open work held up by an inward "is blocked by" link','issue is blocked and issue.resolution is null',                                        'GLOBAL', '2026-07-28 00:00:00', '2026-07-28 00:00:00'),
('preset-stale-two-weeks',    NULL, NULL, 'Stale over two weeks','Not finished and untouched for more than 14 days', 'issue.status.category != ''DONE'' and issue.updatedAt < (now | minusDays(14))',         'GLOBAL', '2026-07-28 00:00:00', '2026-07-28 00:00:00');
