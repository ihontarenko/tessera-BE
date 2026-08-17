
-- =============================================================================
--  V000022  Categories — a tree that belongs to nothing in particular (TSSR-15)
--
--  Universal SQL: MySQL / PostgreSQL compatible. Identical to the mysql copy bar
--  the utf8mb4 header — nothing here references a library table, so V000018's
--  collation problem does not arise.
--
--  ⚠️ BUILT BEFORE THE WIKI AND DELIBERATELY NOT PART OF IT. The first thing filed
--  into this tree is a wiki page, and that is an accident of ordering rather than a
--  property of the tree. `entity_categories` is polymorphic so that FILE — or
--  anything else this product later decides is worth filing — costs a constant in
--  `CategoryEntityType` and NOT a migration of anything that already exists.
--
--  ⚠️ WHICH IS WHY `entity_type` CARRIES NO CHECK CONSTRAINT, against the house
--  form of every other enum-shaped column here (VARCHAR + CHECK, since V000002).
--  A CHECK listing 'PAGE' would make adding 'FILE' exactly the migration this
--  table's shape exists to avoid, and it would buy nothing: the column is written
--  only by `CategoryFilingService`, from an enum, so the set of values it can hold
--  is already closed by the type system.
--
--  ⚠️ A PLAIN `parent_id`, NOT A NESTED SET. Innoventa's categories carry
--  `tree_left`/`tree_right` beside the pointer, which answers "the whole subtree"
--  in one statement and costs a rebuild of the numbering on every move. A project's
--  wiki has tens of categories, not tens of thousands, so the subtree is walked in
--  memory (`CategoryTree`) and a move is one UPDATE. The nested set can be added
--  later without changing a single signature — which is the whole argument for not
--  adding it now.
--
--  ⚠️ THIS TREE IS THE ONLY HIERARCHY (TSSR-5). A wiki page gets no `parent_id` of
--  its own: two hierarchies side by side leave "where does this page actually live"
--  with no good answer. A page is a leaf here, and moving it is re-filing it.
-- =============================================================================

-- ── categories ────────────────────────────────────────────────────────────────
CREATE TABLE categories
(
    id          VARCHAR(36)  NOT NULL,
    project_id  VARCHAR(36)  NOT NULL,
    parent_id   VARCHAR(36),
    name        VARCHAR(128) NOT NULL,
    slug        VARCHAR(128) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,

    CONSTRAINT categories_pk         PRIMARY KEY (id),

    -- ⚠️ UNIQUE PER PROJECT RATHER THAN PER PARENT, and that is a choice rather
    -- than a shortcut. `(project_id, parent_id, slug)` is the obvious constraint
    -- and it does not work: `parent_id` is NULL at the root, and neither engine
    -- treats two NULLs as equal — so the one level where a duplicate is likeliest
    -- would be the one level the database never checked. A slug unique across the
    -- project also makes a category addressable as `…/wiki/c/{slug}` without a
    -- path, which is what a bookmark to a section wants to be.
    CONSTRAINT uq_categories_slug    UNIQUE (project_id, slug),

    CONSTRAINT fk_categories_project FOREIGN KEY (project_id) REFERENCES projects (id),

    -- No cascade: a category with children cannot be deleted at all (the service
    -- refuses and says what is inside), so the database never has to guess what a
    -- vanishing parent means for what was under it.
    CONSTRAINT fk_categories_parent  FOREIGN KEY (parent_id)  REFERENCES categories (id)
);

-- The one read this table has: everything in a project, in tree order. The whole
-- tree is loaded per screen and assembled in memory, so the index covers the sort
-- as well as the filter.
CREATE INDEX index_categories_project ON categories (project_id, parent_id, sort_order);


-- ── entity_categories ─────────────────────────────────────────────────────────
--  Which kind of thing is filed where. The polymorphic half, and the reason the
--  tree is worth building separately from its first consumer.
CREATE TABLE entity_categories
(
    entity_type  VARCHAR(32) NOT NULL,
    entity_id    VARCHAR(36) NOT NULL,
    category_id  VARCHAR(36) NOT NULL,
    created_at   TIMESTAMP   NOT NULL,

    -- ⚠️ THE PRIMARY KEY *IS* THE RULE "ONE CATEGORY PER ENTITY". Folder
    -- semantics, as Innoventa has it: a page is in one place. Many-to-many is a
    -- different product — say so rather than leaving the door open with a
    -- surrogate key and a unique index somebody can drop.
    CONSTRAINT entity_categories_pk          PRIMARY KEY (entity_type, entity_id),

    -- No cascade here either, and for the same reason: a category holding anything
    -- cannot be deleted, so a dangling row has no way to appear.
    CONSTRAINT fk_entity_categories_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- "What is filed here", asked once per category the screen draws, and asked with
-- the kind because a screen showing pages has no interest in files.
CREATE INDEX index_entity_categories_category ON entity_categories (category_id, entity_type);
