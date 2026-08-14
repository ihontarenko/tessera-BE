SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000015  The instance settings row (ticket 06)
--
--  Universal SQL: MySQL / PostgreSQL compatible. The mysql copy is byte-identical
--  bar its utf8mb4 header.
--
--  ⚠️ WHAT THIS REPLACES IS TWO STRING CONSTANTS IN JAVA. `ProjectService` named
--  `scheme-issue-type-default` and `scheme-workflow-default` in its source, which
--  was harmless while schemes were seed-only and read-only. This release makes
--  them deletable and renameable from a screen, and a constant naming a row an
--  administrator may delete is a way to break project creation from the settings
--  page — with the failure arriving at whoever next creates a project, nowhere
--  near the click that caused it.
--
--  ⚠️ ONE ROW, ENFORCED RATHER THAN ASSUMED. The CHECK is what makes "the
--  settings" a fact rather than a habit: without it a second row is insertable,
--  and every reader then has to decide which one it meant. Both databases enforce
--  a CHECK on a literal identically.
--
--  ⚠️ THE FOREIGN KEYS ARE THE OTHER HALF OF THE POINT. A scheme cannot be
--  deleted while it is the instance default — the service refuses it first, with
--  a sentence naming what it is, and the constraint is what makes that refusal
--  impossible to route around.
--
--  Seeded pointing at the schemes V000003 already creates, so an existing
--  installation reads exactly what its source constants used to say.
-- =============================================================================

CREATE TABLE instance_settings
(
    id                            VARCHAR(36) NOT NULL,
    default_issue_type_scheme_id  VARCHAR(36) NOT NULL,
    default_workflow_scheme_id    VARCHAR(36) NOT NULL,
    updated_at                    TIMESTAMP   NOT NULL,

    CONSTRAINT instance_settings_pk         PRIMARY KEY (id),
    CONSTRAINT ck_instance_settings_single  CHECK (id = 'instance'),

    CONSTRAINT fk_instance_settings_issue_type_scheme
        FOREIGN KEY (default_issue_type_scheme_id) REFERENCES issue_type_schemes (id),
    CONSTRAINT fk_instance_settings_workflow_scheme
        FOREIGN KEY (default_workflow_scheme_id)   REFERENCES workflow_schemes (id)
);

INSERT INTO instance_settings (id, default_issue_type_scheme_id, default_workflow_scheme_id, updated_at)
VALUES ('instance', 'scheme-issue-type-default', 'scheme-workflow-default', CURRENT_TIMESTAMP);
