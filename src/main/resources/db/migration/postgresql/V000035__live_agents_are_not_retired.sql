-- =============================================================================
--  V000035  A backfilled mirror is only retired if its agent is gone (TSSR-37)
--
--  ⚠️ FIXING A MISTAKE V000034 MADE, ON PURPOSE AND IN PUBLIC.
--
--  V000034's backfill retired every mirror it created, on the reasoning that a
--  mirror created from history is a record of something that already happened.
--  That reasoning is right for an agent whose `ai_agents` row is gone and WRONG
--  for one still in service — and on this installation BOTH backfilled agents
--  were live. They are the two clients writing these very tickets.
--
--  ⚠️ THE SYMPTOM WOULD HAVE BEEN COSMETIC AND MISLEADING, which is the worst
--  combination: nothing breaks, the clients keep working, and every screen that
--  reads `retired_at` says "Agent off" about a client that is answering
--  requests. Somebody would eventually revoke a connection to fix a problem
--  that was never there.
--
--  ⚠️ A NEW MIGRATION RATHER THAN AN EDIT TO V000034, for the reason V000033
--  was not an edit to V000002: V000034 has run against a live database, and
--  changing it now would make Flyway refuse to start.
-- =============================================================================

--  Un-retire the mirror of every agent that still exists.
--
--  ⚠️ Driven off `ai_agents` rather than off a date or a name. The library's
--  table is the only thing that knows whether an agent is in service; inferring
--  it from when the mirror was written would be a second answer to a question
--  that already has one.
--  ⚠️ PostgreSQL takes no table alias in UPDATE ... SET, so the column is bare here
--  and qualified in the MySQL copy. The one place the two files differ beyond
--  the opening SET NAMES.
UPDATE members
   SET retired_at = NULL
 WHERE kind = 'AGENT'
   AND retired_at IS NOT NULL
   AND EXISTS (SELECT 1 FROM ai_agents live WHERE live.id = members.id);
