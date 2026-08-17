-- =============================================================================
--  V000020  Archived is a state, not a screen (TSSR-4)
--
--  Universal SQL: MySQL / PostgreSQL compatible, and the two copies are
--  byte-identical bar this utf8mb4 header — adding a column is spelled the same
--  by both engines, unlike V000019's widening.
--
--  Finished work had nowhere to go: a closed issue stayed on the board and in
--  every list it was ever in, and the only thing that hid it was one board's
--  done-threshold. `archived_at` is the primitive that fixes it — a second axis,
--  independent of `resolution_id`:
--
--      resolution_id IS NULL  ⇔  open        (ADR-0004, unchanged)
--      archived_at   IS NULL  ⇔  in view
--
--  ⚠️ Two axes rather than one, deliberately. "Finished" and "put away" are
--  different facts: work is resolved the moment it is done, and archived when
--  somebody decides it has stopped being interesting. Collapsing them would mean
--  either the board losing a card the instant it went green, or an archive that
--  cannot be emptied one item at a time.
--
--  ⚠️ Only a closed issue can be archived — enforced in IssueArchiveService, not
--  here. A CHECK constraint would state it, but it would also be re-checked on
--  every unrelated update of a legacy row, and the rule belongs where its refusal
--  can name the reason.
--
--  `archived_by_member_id` carries a foreign key to members, unlike the agent
--  columns beside it: a member row is never discarded, so nothing here has to
--  outlive what it names.
-- =============================================================================

ALTER TABLE issues ADD COLUMN archived_at TIMESTAMP NULL;
ALTER TABLE issues ADD COLUMN archived_by_member_id VARCHAR(36) NULL;

ALTER TABLE issues ADD CONSTRAINT fk_issues_archived_by
    FOREIGN KEY (archived_by_member_id) REFERENCES members (id);

--  Every project-scoped read now asks "…and not archived", so the index carries
--  the project and the flag together rather than leaving the second half to a scan.
CREATE INDEX idx_issues_project_archived ON issues (project_id, archived_at);
