-- =============================================================================
--  V000025  A comment may say what it is about (TSSR-25)
--
--  Universal SQL: MySQL / PostgreSQL compatible, the two copies byte-identical
--  bar the utf8mb4 header the MySQL twin needs.
--
--  A long thread is a wall of prose in which "could not reproduce this on
--  Windows" and "review done, ship it" look identical. A topic makes it
--  scannable without anybody reading it.
--
--  ⚠️ NULLABLE, AND IT STAYS NULLABLE. Most comments are simply comments. A
--  topic every comment had to carry would be a control people got past by
--  picking a lie, which is worse than an unlabelled thread.
--
--  ⚠️ No ON DELETE clause, deliberately. Deleting a topic that comments hold is
--  refused in FlatCatalogWriteService with the count in the message — the rule
--  IS the guard. A cascade would silently strip the label off historic
--  discussion, and SET NULL would do the same more quietly still.
--
--  Unlike an issue type, a topic is inert until somebody picks one, so seeding
--  presets costs a fresh installation nothing and saves it a blank screen.
-- =============================================================================

CREATE TABLE comment_topics
(
    id           VARCHAR(36) NOT NULL,
    name         VARCHAR(64) NOT NULL,
    description  VARCHAR(255),

    CONSTRAINT comment_topics_pk       PRIMARY KEY (id),
    CONSTRAINT uq_comment_topics_name  UNIQUE (name)
);

ALTER TABLE comments ADD COLUMN topic_id VARCHAR(36) NULL;

-- The index goes in BEFORE the foreign key: MySQL creates one of its own for an
-- FK that has none, so declaring the key first leaves the table carrying two
-- indexes over one column. PostgreSQL creates neither and needs this outright.
CREATE INDEX idx_comments_topic ON comments (topic_id);

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_topic FOREIGN KEY (topic_id) REFERENCES comment_topics (id);


-- ═══════════════════════════════ SEED DATA ═════════════════════════════════════

-- Comment topics — what a remark is about. Named for the thing said, never for
-- who says it, so a topic keeps meaning the same when the team changes.
INSERT INTO comment_topics (id, name, description) VALUES
    ('comment-topic-cannot-reproduce', 'Cannot reproduce', 'What was tried, on what, and what happened instead.'),
    ('comment-topic-code-review',      'Code review',      'A review verdict, and what it turned on.'),
    ('comment-topic-root-cause',       'Root cause',       'What actually caused this, once it was known.'),
    ('comment-topic-workaround',       'Workaround',       'How to live with it until it is fixed.'),
    ('comment-topic-decision',         'Decision',         'A choice made here, and why.'),
    ('comment-topic-test-evidence',    'Test evidence',    'What was run, and what it showed.');
