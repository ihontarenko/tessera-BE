-- =============================================================================
--  V000033  An agent is a member, not a field on a comment (TSSR-32)
--
--  ⚠️ A NEW MIGRATION, NOT AN EDIT TO V000002 — and the ticket says otherwise.
--  TSSR-32 was written during the Phase-1 build, when migrations were mutable
--  because Ivan could drop the database freely. That stopped being true: this
--  schema is at V000032 with thirty-one migrations applied and a tracker in
--  daily use, holding the tickets for this very epic. Editing V000002 in place
--  changes its checksum, Flyway refuses to start, and the documented fix is
--  dropping the database — which destroys the work item describing the change.
--
--  ⚠️ `kind`, NOT `parent_id IS NULL`. The two say different things: THIS IS AN
--  AGENT and THIS HAS A PARENT. The moment anything else wants a sub-member —
--  an imported author, a webhook identity — the inference is ambiguous and
--  every query that guessed is wrong.
--
--  ⚠️ `subject` STAYS `NOT NULL` AND UNIQUE. An agent's row gets the synthetic
--  `agent:<agentId>`, which Identity can never mint, so no token can resolve to
--  one. Making the column nullable would weaken the invariant "a member is
--  somebody Identity knows" on behalf of the rows that are exactly the
--  exception.
--
--  ⚠️ AND `retired_at` IS THE NON-NEGOTIABLE OF THE WHOLE EPIC. After this,
--  a comment POINTS at its agent. Deleting the row loses the author of every
--  comment that agent ever wrote — silently, at the moment somebody tidies up
--  their connections. Discard retires; it never deletes.
-- =============================================================================

ALTER TABLE members
    --  PERSON | AGENT. Defaulted so every existing row is a person without an
    --  UPDATE, and every future insert that forgets is one too.
    ADD COLUMN kind       VARCHAR(16) NOT NULL DEFAULT 'PERSON',

    --  Whose it is. ⚠️ RECORD-KEEPING, and nothing that decides whether a call
    --  is allowed may read it — see the ADR. Authorization is settled by
    --  `AgentCallers` reading `AgentAuthority` once; resolving a permission
    --  through this column would be a second permission model beside the
    --  library's, agreeing with it until the afternoon somebody changed one.
    ADD COLUMN parent_id  VARCHAR(36) NULL,

    ADD COLUMN retired_at TIMESTAMP   NULL;

ALTER TABLE members
    ADD CONSTRAINT members_check_kind
        CHECK (kind IN ('PERSON', 'AGENT')),

    --  The two halves of the second kind, stated where they cannot drift: only
    --  an agent has an owner, and only an agent can be switched off.
    ADD CONSTRAINT members_check_agent_shape
        CHECK ((kind = 'PERSON' AND parent_id IS NULL     AND retired_at IS NULL)
            OR (kind = 'AGENT'  AND parent_id IS NOT NULL)),

    --  ⚠️ RESTRICT, and it is the discard rule in the schema. Deleting a person
    --  would otherwise take their agents with it and, through them, the author
    --  of every comment and activity entry those agents wrote.
    ADD CONSTRAINT members_fk_parent
        FOREIGN KEY (parent_id) REFERENCES members (id);

-- Listing somebody's agents, and — far more often — excluding every agent from
-- a directory that is about people. Both are lookups by these two columns.
CREATE INDEX index_members_kind_parent ON members (kind, parent_id);
