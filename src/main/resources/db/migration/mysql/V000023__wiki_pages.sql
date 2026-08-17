SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000023  Wiki pages — prose beside the work (TSSR-16)
--
--  ⚠️ NOT byte-identical to the postgresql copy: `content_markdown` is MEDIUMTEXT
--  here and TEXT there. MySQL's TEXT holds 65535 BYTES, which under utf8mb4 is
--  ~16000 characters — the ceiling an issue description lives under (V000019) and
--  a fair one for a field nobody writes a manual in. A WIKI PAGE IS EXACTLY WHERE
--  SOMEBODY WRITES A MANUAL. MEDIUMTEXT holds 16777215 bytes, so the 200000-
--  character ceiling in `WikiPage.MAXIMUM_MARKDOWN_LENGTH` cannot overflow it
--  whatever alphabet it is written in (200000 x 4 = 800000). PostgreSQL's TEXT is
--  unbounded and needs no second type. Precedent: Innoventa's V102100 uses
--  LONGTEXT the same way.
--
--  ⚠️ NO `parent_id`, AND THAT IS THE DECISION THIS TABLE IS SHAPED BY (TSSR-5).
--  A page is a leaf in the category tree from V000022; moving it is re-filing it,
--  which is a row in `entity_categories` and not a column here. Two hierarchies
--  side by side leave "where does this page actually live" with no good answer.
--
--  ⚠️ NO `stored_file_id`, AND THAT IS A DELIBERATE DEPARTURE FROM THE TICKET.
--  TSSR-16 said the canonical Markdown goes through jmouse-storage with this
--  column as a synced mirror, copying Innoventa's shape. Three facts killed it,
--  and they are worth writing down where the next person will look:
--
--    1. `tessera.file.upload.profile` is ALLOW_DOCUMENTS_AND_IMAGES, an ALLOWLIST
--       of images and office documents. `text/markdown` is not in it, so every
--       page save would be refused by UploadPolicy. CUSTOM does not bind in the
--       library (see application.yml), so narrowing by hand is not available —
--       the only fix would be switching the whole installation to a DENYLIST, and
--       that allowlist is precisely what makes the unauthenticated
--       /api/public/avatars/** route safe.
--    2. `tessera.file.max-size-bytes` is 1 MB, sized for a 256x256 avatar. A long
--       page in Cyrillic passes that, and the ceiling being an avatar's is an
--       accident waiting to refuse somebody's manual.
--    3. WITHOUT VERSION HISTORY THE SECOND COPY BUYS NOTHING. It is rewritten in
--       place on every edit, so it is never a previous version; no read path
--       serves it; and content-addressed deduplication does nothing for text that
--       is unique by definition. It would be a second copy to keep in step, and
--       the only thing a divergence between them could produce is a bug.
--
--  Storage will earn its place in the wiki when pages carry ATTACHMENTS AND
--  IMAGES — bytes that are actually bytes. That is when the column comes back,
--  and it will come back pointing at something.
--
--  ⚠️ NO VERSION HISTORY IN THIS PASS, ruled deliberately — so an update
--  OVERWRITES the text with no way back, and `content_markdown` is the only copy
--  there is. The screen says so out loud rather than leaving somebody to discover
--  it.
-- =============================================================================

CREATE TABLE wiki_pages
(
    id                   VARCHAR(36)  NOT NULL,
    project_id           VARCHAR(36)  NOT NULL,
    title                VARCHAR(255) NOT NULL,
    slug                 VARCHAR(255) NOT NULL,

    -- The only copy of the text. See the header for why this is MEDIUMTEXT here
    -- and TEXT in the postgresql copy, and for why there is no second copy.
    content_markdown     MEDIUMTEXT,

    -- The first plain prose of the document, flattened, so a listing can say what
    -- a page is about without rendering Markdown per row.
    excerpt              VARCHAR(512),

    author_member_id     VARCHAR(36)  NOT NULL,

    -- Who wrote it last. Equal to the author until somebody else edits it, and
    -- nullable only so a row could exist without one — every row this application
    -- writes carries it.
    updated_by_member_id VARCHAR(36),

    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL,

    CONSTRAINT wiki_pages_pk         PRIMARY KEY (id),

    -- A page is addressed as `…/wiki/{slug}` within its project, so the slug has to
    -- be unique there and has no reason to be unique anywhere wider.
    CONSTRAINT uq_wiki_pages_slug    UNIQUE (project_id, slug),

    CONSTRAINT fk_wiki_pages_project FOREIGN KEY (project_id)           REFERENCES projects (id),
    CONSTRAINT fk_wiki_pages_author  FOREIGN KEY (author_member_id)     REFERENCES members (id),
    CONSTRAINT fk_wiki_pages_editor  FOREIGN KEY (updated_by_member_id) REFERENCES members (id)
);

-- The wiki's index: every page in one project, most recently touched first.
CREATE INDEX index_wiki_pages_project ON wiki_pages (project_id, updated_at);
