package net.innoventa.tessera.bootstrap;

/**
 * Something that has to happen once to an installation, and then never again.
 *
 * <p>Flyway answers this for schema. Nothing answered it for <em>data</em>, and the gap is why this
 * exists: the rows a feature is born with — its role bundles, its catalogue entries — used to be
 * {@code INSERT} statements inside a migration, which works exactly once and cannot be expressed in
 * anything but SQL. Tessera's roles now come out of {@code policy/tessera.jmp}, which is a file
 * somebody reads and reviews rather than a wall of keys, and something has to turn it into rows.
 *
 * <p>A step is a bean where its subject lives; {@link BootstrapLedger} finds them by type and runs the
 * ones that have not run.
 *
 * <h2>⚠️ Idempotent anyway</h2>
 *
 * <p>The ledger decides whether a step <em>should</em> run; the step's own writes have to survive being
 * asked twice regardless. That is what makes the recovery path safe — somebody clearing a ledger row to
 * re-run a step must not have to reason about what is already there.
 */
public interface BootstrapStep {

    /**
     * This step's identity, stable forever.
     *
     * <p>⚠️ <strong>Renaming one after it has run makes it a different step</strong>, and a different
     * step has never run — so it runs again on the next start. Conventionally namespaced:
     * {@code access:policy}.
     */
    String key();

    /**
     * What this step would write, hashed.
     *
     * <p>⚠️ Usually the source it reads — the policy file's text — but not always <em>only</em> that. A
     * step whose output depends on something else has to fold that in, or it will not notice when the
     * something else changes.
     */
    String checksum();

    /** What this step is for, in words. Recorded beside the row, for whoever reads the table later. */
    String note();

    /**
     * Does the work.
     *
     * <p>Runs inside a transaction the ledger opens, together with the row recording it — so a step that
     * throws leaves neither its writes nor a ledger entry claiming it ran.
     */
    Outcome apply();

    /**
     * What a step changed.
     *
     * <p>A step that answers with nothing is a step nobody can debug: the first question about a seed is
     * always "did it actually write anything", and a log line saying "ran" does not answer it.
     *
     * @param rowsWritten how many rows it created or updated
     * @param summary     one line a human can read
     */
    record Outcome(int rowsWritten, String summary) {

        public static Outcome nothing() {
            return new Outcome(0, "nothing to do");
        }
    }
}
