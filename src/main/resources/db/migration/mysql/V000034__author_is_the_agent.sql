SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000034  The author IS the agent — the columns go (TSSR-34, TSSR-37)
--
--  ⚠️ THE BACKFILL AND THE DROP ARE ONE MIGRATION, AND THAT IS LOAD-BEARING.
--  TSSR-37 ships in the same deployment as TSSR-34. Between them there is a
--  window in which `agent_id` still holds the truth and `author_member_id`
--  does not yet — a deployment that stopped in the middle would render every
--  agent-written comment as its owner's, and nobody would know which ones.
--
--  ⚠️ AND TSSR-35 LANDED FIRST, WHICH IS WHY THIS IS SAFE. "Mine" already
--  means me OR one of my agents, so the moment `author_member_id` starts
--  naming an agent, its owner can still edit and delete what it wrote. In the
--  other order this migration is the exact moment people lose their own
--  comments.
-- =============================================================================

--  ── The backfill (TSSR-37) ───────────────────────────────────────────────────
--
--  ⚠️ EVERY AGENT THAT EVER WROTE ANYTHING GETS A MIRROR, including agents whose
--  `ai_agents` row is long gone. The name is the SNAPSHOT the old column kept —
--  which is the one moment that stale copy is worth more than a join, because
--  it is the only surviving record of what the agent was called.
--
--  ⚠️ `parent_id` is the author it was recorded beside. The old pair was written
--  "beside the author, never instead of it", so the person on the row IS the
--  owner — that is what makes this backfill possible at all rather than a
--  guess.
--
--  ⚠️ Retired on arrival. A mirror created here is a record of something that
--  already happened; presenting it as a live agent would put dead clients in
--  every listing that means "what is connected".
INSERT INTO members (id, subject, display_name, email, system_role, kind, parent_id,
                     retired_at, avatar_kind, avatar_preset, created_at, updated_at)
SELECT written.agent_id,
       CONCAT('agent:', written.agent_id),
       COALESCE(written.agent_name, 'Retired client'),
       NULL,
       'USER',
       'AGENT',
       written.owner_id,
       CURRENT_TIMESTAMP,
       'PRESET',
       written.agent_id,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (
    --  One row per agent, taking whichever name and owner it was last seen with.
    SELECT agent_id,
           MAX(agent_name) AS agent_name,
           MIN(owner_id)   AS owner_id
    FROM (
        SELECT agent_id, agent_name, author_member_id AS owner_id
        FROM comments
        WHERE agent_id IS NOT NULL
        UNION ALL
        SELECT agent_id, agent_name, actor_member_id AS owner_id
        FROM activity_logs
        WHERE agent_id IS NOT NULL
    ) AS every_mention
    GROUP BY agent_id
) AS written
--  An agent still in service already has a live mirror from V000033's seam.
--  ⚠️ Left as it is rather than overwritten: that row is current, and this one
--  would replace a live agent's name with a snapshot and retire it.
WHERE NOT EXISTS (SELECT 1 FROM members existing WHERE existing.id = written.agent_id)
  --  The owner has to still exist. An agent whose owner was removed has nothing
  --  to hang from, and the FK would refuse the insert anyway — better to skip it
  --  and leave the old columns' history unresolvable than to fail the migration.
  AND EXISTS (SELECT 1 FROM members owner WHERE owner.id = written.owner_id);

--  ── The repointing (TSSR-34) ────────────────────────────────────────────────
--
--  From here the author IS the agent. ⚠️ Only where the mirror exists — an agent
--  skipped above keeps its owner as the author, which is exactly what the row
--  said before this migration and is therefore no worse than it was.
UPDATE comments
   SET author_member_id = agent_id
 WHERE agent_id IS NOT NULL
   AND EXISTS (SELECT 1 FROM members mirror WHERE mirror.id = comments.agent_id);

UPDATE activity_logs
   SET actor_member_id = agent_id
 WHERE agent_id IS NOT NULL
   AND EXISTS (SELECT 1 FROM members mirror WHERE mirror.id = activity_logs.agent_id);

--  ── The columns go ──────────────────────────────────────────────────────────
--
--  ⚠️ What is being given up is the SNAPSHOT, deliberately. `agent_name` never
--  went stale because nothing updated it; from now the name is a join and a
--  rename is visible everywhere at once. The price is that history now depends
--  on a row — which is why V000033's discard RETIRES rather than deletes, and
--  why that rule is the non-negotiable of the epic.
ALTER TABLE comments
    DROP COLUMN agent_id,
    DROP COLUMN agent_name;

ALTER TABLE activity_logs
    DROP COLUMN agent_id,
    DROP COLUMN agent_name;
