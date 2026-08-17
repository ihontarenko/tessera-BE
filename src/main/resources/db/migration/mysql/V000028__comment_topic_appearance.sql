SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000028  A comment topic is drawn, not just named (TSSR-30)
--
--  Universal SQL: MySQL / PostgreSQL compatible, the two copies byte-identical
--  bar the utf8mb4 header the MySQL twin needs.
--
--  A topic was a word on a chip. With an icon and a colour it is something a
--  reader recognises without reading, and a comment carrying one gets a thin
--  accent down its left edge in the topic's own colour.
--
--  ⚠️ TWO FIELDS, NOT ONE, AND THEY ARE NOT THE SAME KIND OF THING.
--
--    icon_key  a key from a CLOSED list (CommentTopicIcons). The server cannot
--              hold React components, so it holds the name of what the topic is
--              and the client decides what that looks like. An unknown key
--              draws the generic mark with no error anywhere — which is why the
--              write service refuses one rather than storing it.
--
--    color     any CSS colour, refused for nothing. Same as statuses.color and
--              priorities.color: `#8b5cf6`, `rebeccapurple` and
--              `hsl(258 90% 66%)` are all valid, so a CHECK could only
--              recognise the hex spelling — rejecting good values while still
--              passing `#zzzzzz`.
--
--  ⚠️ An issue type's colour is DERIVED from its icon key by one client-side
--  map; a topic's is stored beside it. That divergence is deliberate: a topic
--  carries the two independently, and the stored-colour precedent is the one
--  statuses already set.
--
--  Both nullable. A topic with neither renders exactly as it did yesterday.
-- =============================================================================

ALTER TABLE comment_topics ADD COLUMN icon_key VARCHAR(64) NULL;
ALTER TABLE comment_topics ADD COLUMN color VARCHAR(16) NULL;


-- ═══════════════════════════════ SEED DATA ═════════════════════════════════════

-- The six seeded topics, dressed. Hues chosen apart from one another and from
-- what the issue-type icons already use, so a comment accent never reads as a
-- type accent (TSSR-22) on the same screen.
UPDATE comment_topics SET icon_key = 'cannot-reproduce', color = '#f59e0b' WHERE id = 'comment-topic-cannot-reproduce';
UPDATE comment_topics SET icon_key = 'code-review',      color = '#8b5cf6' WHERE id = 'comment-topic-code-review';
UPDATE comment_topics SET icon_key = 'root-cause',       color = '#f43f5e' WHERE id = 'comment-topic-root-cause';
UPDATE comment_topics SET icon_key = 'workaround',       color = '#14b8a6' WHERE id = 'comment-topic-workaround';
UPDATE comment_topics SET icon_key = 'decision',         color = '#0ea5e9' WHERE id = 'comment-topic-decision';
UPDATE comment_topics SET icon_key = 'test-evidence',    color = '#10b981' WHERE id = 'comment-topic-test-evidence';
