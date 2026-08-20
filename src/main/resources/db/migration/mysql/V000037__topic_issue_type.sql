SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000037  Topic — one description, and sub-tasks under it
--
--  Universal SQL: MySQL / PostgreSQL compatible, the two copies byte-identical
--  bar the utf8mb4 header the MySQL twin needs.
--
--  A TOPIC is a work item whose point is its DESCRIPTION. The context is
--  written once, at the top, and what hangs beneath it are sub-tasks — steps
--  rather than units of work, none of which deserves a description of its own.
--
--  ⚠️ LEVEL 0, WHICH IS WHERE IT DIFFERS FROM AN EPIC. Not "a smaller Epic":
--  an Epic at level 1 holds stories, each a card with its own life, its own
--  estimate and its own place in a sprint. A Topic IS the card — it is what a
--  board draws, what a sprint plans and what carries the points (ADR-0014) —
--  and its children at level −1 are a checklist, not a backlog. "A pack of
--  cards" and "one card with steps" are different shapes of work, and the
--  level is what makes the difference real rather than a matter of intent.
--
--  ⚠️ THE OBJECTION THIS SURVIVED, RECORDED SO IT IS NOT RE-RAISED. A second
--  CONTAINER beside Epic at level 1 would be a tag wearing a type's clothes —
--  nothing in the model would tell the two apart, so nobody would remember
--  which to reach for. That argument does not reach level 0, which is already
--  and deliberately semantic: Story, Task, Bug and the rest share one
--  mechanism and differ only in what they claim about the work. Topic claims
--  "the context is here, the work is the steps below", the way Bug claims
--  "something was wrong".
--
--  ⚠️ NOT A HUB EITHER. A Hub at level 2 gathers epics ACROSS projects and is
--  the only type that crosses that boundary. A Topic never leaves its project
--  and holds nothing but its own sub-tasks. They sit at opposite ends of the
--  hierarchy on purpose.
--
--  Appended at the end of the default scheme; any administrator can reorder it
--  on the scheme screen.
-- =============================================================================

INSERT INTO issue_types (id, name, hierarchy_level, icon_key, description) VALUES
    ('issue-type-topic', 'Topic', 0, 'documentation',
     'Shared context for a group of sub-tasks. The description is the work item; the sub-tasks beneath it are its steps.');

INSERT INTO issue_type_scheme_items (id, scheme_id, issue_type_id, sequence) VALUES
    ('itsi-default-topic', 'scheme-issue-type-default', 'issue-type-topic', 10);
