package net.innoventa.tessera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * ⚠️ <strong>The entity scan is widened by hand because two libraries own tables this service stores
 * rows in.</strong> {@code jmouse-ai-jpa} owns {@code ai_pending_confirmations} — a preview waiting for
 * the call that confirms it — along with the call counters and provider settings.
 * {@code jmouse-access-jpa} owns every {@code access_*} table: the roles, their bundles, who holds
 * them, and the personal allow or deny that beats them. {@code jmouse-storage-jpa} owns
 * {@code stored_files} — one row per set of bytes anybody uploaded, and what {@code members.avatar_file_id}
 * points at.
 *
 * <p><strong>Whoever owns the table owns the mapping.</strong> A Tessera {@code @Entity} over a
 * library's schema would be kept honest only by {@code ddl-auto: validate}, which is a hope rather than
 * a contract — so there is none, and {@code LibraryOwnedTablesTest} is what stops one growing back.
 *
 * <p>⚠️ Forgetting either line fails late and misleadingly. Hibernate validates only what it was given,
 * so {@code ddl-auto: validate} passes, the service starts, and the first query dies with
 * <em>"could not resolve root entity"</em> — from inside a library, on whatever request happens to
 * reach it first. The tables themselves will be there, because both libraries bring their own
 * migrations.
 */
@SpringBootApplication
@EntityScan({"net.innoventa.tessera", "org.jmouse.ai.jpa.entity", "org.jmouse.access.jpa.entity",
             "org.jmouse.storage.jpa"})
public class TesseraApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(TesseraApplication.class, arguments);
    }

}
