-- =============================================================================
--  V000016  Estimation becomes a scheme (ticket 07, ADR-0019)
--
--  Universal SQL: MySQL / PostgreSQL compatible. The mysql copy is byte-identical
--  bar its utf8mb4 header. Identifiers are full words or single-letter
--  abbreviations — `esi-` is the single-letter form of "Estimation Scheme Item",
--  the same precedent `itsi-` set in V000003.
--
--  ⚠️ AN ITEM IS A (label, weight) PAIR, AND `issues.story_points` GOES ON
--  STORING THE WEIGHT. `XL` is stored as 8. That one decision is why this
--  migration adds tables and changes nothing: burndown, velocity,
--  `story_points_at_add` and every jME filter add numbers today and add the same
--  numbers afterwards. Nothing in the reporting layer learns that scales exist.
--
--  ⚠️ `projects.estimation_scheme_id` IS NULLABLE, AND NULL IS THE ANSWER "this
--  project does not estimate" — not a scheme named None. A project that does not
--  estimate has no story-points control at all, which an empty select cannot say.
--
--  ⚠️ THE LAST-SCHEME RULE DOES NOT APPLY HERE, unlike the other two kinds. An
--  installation with no estimation schemes is coherent and merely means nobody
--  estimates; an installation with no issue-type schemes is a tracker whose next
--  project cannot be created.
-- =============================================================================

-- ── estimation_schemes ────────────────────────────────────────────────────────
CREATE TABLE estimation_schemes
(
    id           VARCHAR(36) NOT NULL,
    name         VARCHAR(64) NOT NULL,
    description  VARCHAR(255),

    CONSTRAINT estimation_schemes_pk       PRIMARY KEY (id),
    CONSTRAINT uq_estimation_schemes_name  UNIQUE (name)
);

-- ── estimation_scheme_items ───────────────────────────────────────────────────
--  Mirrors issue_type_scheme_items exactly: flat id, scheme reference, and a
--  `sequence` giving the scheme its ordered list.
--
--  ⚠️ Two items may share a weight with different labels — nothing breaks, and
--  the reverse lookup then takes the first in order. That is a documented
--  consequence rather than a tie-break rule worth inventing.
CREATE TABLE estimation_scheme_items
(
    id         VARCHAR(36)      NOT NULL,
    scheme_id  VARCHAR(36)      NOT NULL,
    label      VARCHAR(32)      NOT NULL,
    weight     DOUBLE PRECISION NOT NULL,
    sequence   INTEGER          NOT NULL,

    CONSTRAINT estimation_scheme_items_pk    PRIMARY KEY (id),
    CONSTRAINT uq_estimation_scheme_item     UNIQUE (scheme_id, label),
    CONSTRAINT fk_estimation_scheme_item     FOREIGN KEY (scheme_id) REFERENCES estimation_schemes (id)
);

-- ── How a project estimates ───────────────────────────────────────────────────
ALTER TABLE projects ADD COLUMN estimation_scheme_id VARCHAR(36);
ALTER TABLE projects ADD CONSTRAINT fk_projects_estimation_scheme
    FOREIGN KEY (estimation_scheme_id) REFERENCES estimation_schemes (id);

-- ── And what a new one starts on ──────────────────────────────────────────────
--  Nullable, and seeded null: an installation whose default is "does not estimate"
--  is the honest starting point, since nothing here knows how a team works.
ALTER TABLE instance_settings ADD COLUMN default_estimation_scheme_id VARCHAR(36);
ALTER TABLE instance_settings ADD CONSTRAINT fk_instance_settings_estimation_scheme
    FOREIGN KEY (default_estimation_scheme_id) REFERENCES estimation_schemes (id);


-- ═══════════════════════════════ SEED DATA ═════════════════════════════════════

INSERT INTO estimation_schemes (id, name, description) VALUES
    ('scheme-estimation-fibonacci',     'Fibonacci',     'The classic planning-poker scale: 1, 2, 3, 5, 8, 13, 21.'),
    ('scheme-estimation-t-shirt',       'T-shirt',       'Sizes rather than numbers, stored as the Fibonacci weights behind them.'),
    ('scheme-estimation-powers-of-two', 'Powers of two', 'Doubling scale: 1, 2, 4, 8, 16.'),
    ('scheme-estimation-linear',        'Linear',        'One to ten, for teams that estimate in days.');

-- Fibonacci — label and weight are the same number, which is the ordinary case.
INSERT INTO estimation_scheme_items (id, scheme_id, label, weight, sequence) VALUES
    ('esi-fibonacci-1',  'scheme-estimation-fibonacci', '1',  1,  0),
    ('esi-fibonacci-2',  'scheme-estimation-fibonacci', '2',  2,  1),
    ('esi-fibonacci-3',  'scheme-estimation-fibonacci', '3',  3,  2),
    ('esi-fibonacci-5',  'scheme-estimation-fibonacci', '5',  5,  3),
    ('esi-fibonacci-8',  'scheme-estimation-fibonacci', '8',  8,  4),
    ('esi-fibonacci-13', 'scheme-estimation-fibonacci', '13', 13, 5),
    ('esi-fibonacci-21', 'scheme-estimation-fibonacci', '21', 21, 6);

-- T-shirt — the case the whole design is for: XL is displayed as XL and summed as 8.
INSERT INTO estimation_scheme_items (id, scheme_id, label, weight, sequence) VALUES
    ('esi-t-shirt-extra-small',       'scheme-estimation-t-shirt', 'XS',  1,  0),
    ('esi-t-shirt-small',             'scheme-estimation-t-shirt', 'S',   2,  1),
    ('esi-t-shirt-medium',            'scheme-estimation-t-shirt', 'M',   3,  2),
    ('esi-t-shirt-large',             'scheme-estimation-t-shirt', 'L',   5,  3),
    ('esi-t-shirt-extra-large',       'scheme-estimation-t-shirt', 'XL',  8,  4),
    ('esi-t-shirt-extra-extra-large', 'scheme-estimation-t-shirt', 'XXL', 13, 5);

INSERT INTO estimation_scheme_items (id, scheme_id, label, weight, sequence) VALUES
    ('esi-powers-of-two-1',  'scheme-estimation-powers-of-two', '1',  1,  0),
    ('esi-powers-of-two-2',  'scheme-estimation-powers-of-two', '2',  2,  1),
    ('esi-powers-of-two-4',  'scheme-estimation-powers-of-two', '4',  4,  2),
    ('esi-powers-of-two-8',  'scheme-estimation-powers-of-two', '8',  8,  3),
    ('esi-powers-of-two-16', 'scheme-estimation-powers-of-two', '16', 16, 4);

INSERT INTO estimation_scheme_items (id, scheme_id, label, weight, sequence) VALUES
    ('esi-linear-1',  'scheme-estimation-linear', '1',  1,  0),
    ('esi-linear-2',  'scheme-estimation-linear', '2',  2,  1),
    ('esi-linear-3',  'scheme-estimation-linear', '3',  3,  2),
    ('esi-linear-4',  'scheme-estimation-linear', '4',  4,  3),
    ('esi-linear-5',  'scheme-estimation-linear', '5',  5,  4),
    ('esi-linear-6',  'scheme-estimation-linear', '6',  6,  5),
    ('esi-linear-7',  'scheme-estimation-linear', '7',  7,  6),
    ('esi-linear-8',  'scheme-estimation-linear', '8',  8,  7),
    ('esi-linear-9',  'scheme-estimation-linear', '9',  9,  8),
    ('esi-linear-10', 'scheme-estimation-linear', '10', 10, 9);
