SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000040  Tessera's wiki points at Tessera's own section
--
--  Ivan, 2026-08-21: «тессера має вказувати на власну папку а не на тестове
--  ЩОСЬ!»
--
--  It pointed at `d3d1a953…` — a throwaway section somebody made called
--  "Publicity Test" — because that pointer was set by hand on a screen and never
--  revisited. Kiwi seeds a real `Tessera` section in its own `V000016`, and this
--  is the other half.
--
--  ⚠️ THE VALUE IS AN IDENTIFIER IN ANOTHER PRODUCT'S DATABASE, and there is no
--  foreign key to write. Kiwi owns that tree: a section deleted there stops
--  resolving here, and the screen SAYS so ("this wiki is not yours to read")
--  rather than this schema preventing it. `KW-0065` argues the column should hold
--  a provider and an opaque handle instead of being named after whichever product
--  answers today; this migration does not decide that.
--
--  ⚠️ AND IT GRANTS NOBODY ANYTHING. Pointing a project at a branch decides what
--  its wiki tab SHOWS. Who may read it is Kiwi's answer, per section, through its
--  own grants — a member without one sees an empty tree here exactly as they
--  would there.
-- =============================================================================

UPDATE projects
   SET kiwi_root_category_id = 'category-tessera'
 WHERE project_key = 'TSSR';
