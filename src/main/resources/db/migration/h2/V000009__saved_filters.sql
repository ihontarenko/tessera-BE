-- =============================================================================
--  V000009  Saved filters — a named, reusable board filter (ADR-0008)
--
--  Universal SQL: H2 / MySQL / PostgreSQL compatible. Identical to the mysql copy
--  bar the utf8mb4 header (no backslashes to double, no dialect-specific
--  functions). Enums as VARCHAR + CHECK; timestamps maintained by the application
--  (@PrePersist / @PreUpdate), not triggers. Identifiers are full words or
--  single-letter abbreviations, never 2-4 letter truncations —
--  `owner_member_id`, `expression`, `visibility`.
--
--  A saved filter gets its OWN TABLE rather than a JSON blob hung off the board,
--  because everything ordinary about it is a query: list what a member saved,
--  share one with the project, rename one, find every filter that mentions a
--  component before deleting that component. None of those are answerable against
--  serialised text.
--
--  What is stored is the PREDICATE — `issue.assignee == currentMember` — and never
--  the collection pipeline that runs it. That is what keeps two filters
--  composable as `(a) and (b)`, and what lets the same string be handed to
--  jmouse-jdbc as a WHERE clause when Phase 4 needs filters that outgrow one
--  board's loaded slice.
--
--  `expression` is bounded at 1024 to match the evaluator's own limit
--  (BoardFilterEvaluator.MAXIMUM_EXPRESSION_LENGTH), so the schema cannot accept a
--  filter the engine would then refuse.
--
--  One invariant is NOT expressed here and lives in the service layer: an
--  expression must parse and must return a boolean. That needs the jME engine, not
--  SQL — the same reasoning as V000008's service-enforced sprint invariants.
--
--  `project_id` and `owner_member_id` are NULLABLE, and only for GLOBAL rows: the
--  presets the product ships (V000010) belong to no project and to no member,
--  which is precisely what makes them universal. Nothing else may leave them null —
--  a PRIVATE or PROJECT filter without an owner would be a filter nobody could
--  edit or delete. The service layer enforces that pairing, because the condition
--  ("null exactly when GLOBAL") is a three-way CHECK that MySQL 8.4 would accept
--  and silently under-enforce on older engines.
-- =============================================================================

CREATE TABLE saved_filters
(
    id               VARCHAR(36)   NOT NULL,
    project_id       VARCHAR(36),
    owner_member_id  VARCHAR(36),
    name             VARCHAR(128)  NOT NULL,
    description      VARCHAR(500),
    expression       VARCHAR(1024) NOT NULL,
    visibility       VARCHAR(16)   NOT NULL
                         CHECK (visibility IN ('PRIVATE', 'PROJECT', 'GLOBAL')),
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL,

    CONSTRAINT saved_filters_pk               PRIMARY KEY (id),
    -- Unique per owner, not per project: two members may each keep their own "My bugs".
    CONSTRAINT uq_saved_filters_owner_name    UNIQUE (project_id, owner_member_id, name),
    CONSTRAINT fk_saved_filters_project       FOREIGN KEY (project_id)      REFERENCES projects (id),
    CONSTRAINT fk_saved_filters_owner_member  FOREIGN KEY (owner_member_id) REFERENCES members (id)
);

-- The filter list is always asked project-first, for one member plus whatever the
-- project shares — this index serves both halves of that read. Global presets carry
-- a null project_id and are picked up by the same query's visibility branch.
CREATE INDEX idx_saved_filters_project_visibility ON saved_filters (project_id, visibility);
