-- ============================================================================
--  V000043  An issue gets three dates, and each answers a different question
-- ----------------------------------------------------------------------------
--  A priority says how much something matters. It does not say what to pick up
--  THIS MORNING, and it never could — a backlog where forty issues are "High"
--  is a backlog with no order, and re-ranking forty rows to push one forward is
--  not a thing anybody does twice.
--
--  So an issue now carries a small schedule, three nullable dates, and they are
--  three separate facts rather than three settings of one:
--
--    queued_for  WHEN I INTEND TO PICK IT UP. My own plan, moved freely, and
--                worth nothing to anybody else. "Today" and "Tomorrow" are this
--                column.
--    red_line    THE DAY IT STOPS BEING COMFORTABLE. A warning I set for
--                myself, ahead of the commitment.
--    deadline    THE DAY IT IS DUE. A commitment to somebody else.
--
--  ⚠️ DATE, NOT TIMESTAMP, AND THAT IS THE WHOLE POINT OF THE COLUMN TYPE.
--
--  Every one of these is a thing a person says about a DAY — "today", "Friday",
--  "the fifth". A timestamp would force a time nobody meant onto each of them,
--  and then compare it against a clock: an issue queued for today would stop
--  being queued at some hour of that day, for no reason a reader could name.
--  A date compares against a date, and rolls itself over at midnight with no
--  job to run.
--
--  ⚠️ `queued_for` IS THE ONE THAT IS CLEARED AUTOMATICALLY, and the asymmetry
--  is deliberate. Finishing an issue answers "when do I pick this up" — nothing
--  is picked up twice — so `TransitionService` clears it on the way into a DONE
--  status, beside `resolved_at`. The other two are history: what the commitment
--  WAS is worth keeping beside when the work actually landed, and erasing it on
--  completion would delete the only evidence of whether it was met.
--
--  ⚠️ TWO INDEXES, NOT THREE. `queued_for` and `deadline` are what a listing
--  narrows and orders by; `red_line` is only ever read on rows already fetched,
--  because "past its red line" is answered from a row rather than searched for.
--  An index nothing queries is a write cost with no read behind it.
-- ============================================================================

ALTER TABLE issues
    ADD COLUMN queued_for DATE NULL,
    ADD COLUMN red_line   DATE NULL,
    ADD COLUMN deadline   DATE NULL;

CREATE INDEX ix_issues_queued_for ON issues (queued_for);
CREATE INDEX ix_issues_deadline ON issues (deadline);
