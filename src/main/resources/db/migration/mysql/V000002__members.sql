SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000002  Members — the local person record (ADR-0002; CONTEXT.md "Member")
--
--  Universal SQL: H2 / MySQL / PostgreSQL compatible. No database-specific types.
--  Enums as VARCHAR + CHECK. updated_at maintained by the application layer
--  (@PreUpdate), not triggers. The mysql copy is identical bar the utf8mb4 header
--  and any doubled backslashes (there are none here).
--
--  A Member is provisioned the first time an Identity subject presents a token
--  (MemberService.resolveMember). No workspace/organization column — Project is
--  top-level (ADR-0002); visibility is membership-based, not tenant-based.
-- =============================================================================

CREATE TABLE members
(
    id            VARCHAR(36)  NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255),
    email         VARCHAR(255),
    system_role   VARCHAR(16)  NOT NULL DEFAULT 'USER'
                      CHECK (system_role IN ('ADMIN', 'USER')),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,

    CONSTRAINT members_pk             PRIMARY KEY (id),
    CONSTRAINT uq_members_subject     UNIQUE (subject)
);
