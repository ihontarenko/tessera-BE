SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
--  V000017  Model Context Protocol credentials — one approved client connection
--
--  Universal SQL: MySQL / PostgreSQL compatible. The mysql copy is byte-identical
--  bar its utf8mb4 header.
--
--  ⚠️ WHAT THIS TABLE IS NOT: the access token. That is a short-lived JWT Tessera
--  signs with its own secret and never stores. What a row IS is the CONNECTION —
--  the approval a person gave a client, the credential that renews it, and the
--  switch that ends it. An access token names its row in a `cid` claim, and every
--  protocol call checks the row is still there, which is the only reason a
--  self-contained token is safe to hand out for a month.
--
--  ⚠️ THE REFRESH TOKEN IS A DIGEST, NEVER ITSELF. A readable refresh token in a
--  table is a credential anybody with a database session can use as its holder.
--  SHA-256 hex of 32 random bytes: there is nothing to slow a guesser down for, so
--  a password hash here would be cost without benefit. UNIQUE because a lookup by
--  what a client presents must find one row or none.
--
--  ⚠️ THE FOREIGN KEY IS THE POINT OF STORING member_id RATHER THAN A SUBJECT.
--  What the token carries is Identity's `sub`; what Tessera authorizes is always
--  the local member row, and a credential outliving the person it acts as is not
--  a state this schema can reach.
--
--  Nothing is seeded. A connection exists only because somebody approved a screen.
-- =============================================================================

CREATE TABLE mcp_credentials
(
    id                  VARCHAR(36)  NOT NULL,
    member_id           VARCHAR(36)  NOT NULL,
    client_name         VARCHAR(255) NOT NULL,
    refresh_token_hash  VARCHAR(64)  NOT NULL,
    refresh_expires_at  TIMESTAMP    NOT NULL,
    issued_at           TIMESTAMP    NOT NULL,
    last_used_at        TIMESTAMP,
    revoked_at          TIMESTAMP,

    CONSTRAINT mcp_credentials_pk          PRIMARY KEY (id),
    CONSTRAINT uq_mcp_credentials_refresh  UNIQUE (refresh_token_hash),

    CONSTRAINT fk_mcp_credentials_member
        FOREIGN KEY (member_id) REFERENCES members (id)
);

-- Every listing is "this person's connections, newest first" — the account screen
-- and nothing else reads this table by anything but its primary key.
CREATE INDEX ix_mcp_credentials_member ON mcp_credentials (member_id, issued_at);
