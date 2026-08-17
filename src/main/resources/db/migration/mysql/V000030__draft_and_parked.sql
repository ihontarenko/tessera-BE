SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000030  Draft and Parked: work that is kept but cannot be started (TSSR-42)
--
--  Universal SQL: MySQL / PostgreSQL compatible, the two copies byte-identical
--  bar the utf8mb4 header the MySQL twin needs.
--
--  A DRAFT IS NOT THOUGHT THROUGH YET. PARKED IS THOUGHT THROUGH, AND DECIDED
--  NOT NOW. Two reasons a ticket is not ready to be picked up, and they are not
--  the same reason — one is unfinished, the other is scheduled against.
--
--  ⚠️ INSERTS ONLY. NO DDL, AND THAT IS THE POINT. The state machine here is
--  already data: statuses are rows, transitions are rows, and the engine
--  refuses any move that is not an edge. So the gate — "this cannot be taken
--  into work" — is A MISSING ROW rather than a flag anybody has to check.
--  There is no edge from either of these to In Progress, and no code knows
--  they are special.
--
--  ⚠️ The test that this is modelled correctly: if it needed a new
--  StatusCategory, it would be wrong. It does not. Both are open, unstarted
--  work, which is exactly TODO.
--
--  ⚠️ NOT ISSUE TYPES. Readiness is a state, not a kind of work — a draft BUG
--  is still a bug. As a type it would have no honest hierarchy_level, would
--  need adding to every issue_type_scheme, and would take the issue's own icon
--  and board accent away from it.
--
--  ⚠️ THIS CHANGES EXISTING PROJECTS. Transitions belong to a workflow and
--  projects reach a workflow through a scheme, so every project on
--  workflow-default gets both states as soon as this runs. Intended, but not an
--  addition nobody notices.
--
--  ⚠️ Drafts WILL show on Kanban boards, in the To Do column: an unmapped
--  status falls to the first column claiming its category (BoardColumnResolver).
--  Left that way on purpose — dragging one into WIP goes through the same
--  engine and is refused. An administrator who wants them off maps To Do
--  explicitly and clears that column's category fallback, after which unmapped
--  statuses live in the backlog (ADR-0016). That is a per-board choice, and
--  rewriting everybody's board configuration from a migration is not this
--  ticket's business.
-- =============================================================================

-- Both TODO: open, unstarted. Coloured (TSSR-21) so they are told apart from
-- ready work at a glance — zinc for the one that is not real yet, slate for the
-- one that is a decision.
INSERT INTO statuses (id, name, category, color) VALUES
    ('status-draft',  'Draft',  'TODO', '#a1a1aa'),
    ('status-parked', 'Parked', 'TODO', '#64748b');

-- ⚠️ workflow-default only. workflow-todo is the deliberately flat, lightweight
-- one; giving it two pre-states is the opposite of what it is for.
--
-- ⚠️ There is deliberately NO transition into status-in-progress from either,
-- and no second create transition: the create edge is the one with a null
-- from_status_id, and a second would make "what status does a new issue get"
-- ambiguous. A half-written ticket is created in To Do and demoted.
--
-- ⚠️ And no In Progress → Parked. Work that started and stopped is a different
-- thing; Stop Progress already returns it to To Do first, and adding the edge
-- would make Parked mean two things.
INSERT INTO transitions (id, workflow_id, from_status_id, to_status_id, name) VALUES
    ('transition-default-to-draft',    'workflow-default', 'status-to-do',  'status-draft',  'Send back to draft'),
    ('transition-default-draft-ready', 'workflow-default', 'status-draft',  'status-to-do',  'Ready'),
    ('transition-default-park',        'workflow-default', 'status-to-do',  'status-parked', 'Park'),
    ('transition-default-unpark',      'workflow-default', 'status-parked', 'status-to-do',  'Unpark'),
    ('transition-default-draft-park',  'workflow-default', 'status-draft',  'status-parked', 'Park'),
    ('transition-default-park-draft',  'workflow-default', 'status-parked', 'status-draft',  'Back to draft');

-- The reason lives beside the state, never inside it. "Frozen", "not fully
-- decided", "no confidence", "needs detail" are ONE state with different
-- reasons; five statuses would be five columns nobody can tell apart.
--
-- Parking's reason goes under the existing Decision topic — parking IS a
-- decision, and a third topic saying so would be a synonym.
INSERT INTO comment_topics (id, name, description, icon_key, color) VALUES
    ('comment-topic-needs-shaping', 'Needs shaping', 'What is still missing before this can be picked up.', 'note',     '#64748b'),
    ('comment-topic-open-question', 'Open question', 'The one thing that is undecided, and who can decide it.', 'question', '#f97316');
