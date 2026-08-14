package net.innoventa.tessera.bootstrap;

import net.innoventa.tessera.domain.BootstrapRecord;
import net.innoventa.tessera.repository.BootstrapRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs every {@link BootstrapStep} that has not run, once.
 *
 * <p>What Flyway is to schema, this is to the rows a feature is born with.
 *
 * <h2>⚠️ One transaction per step, holding both the work and the record</h2>
 *
 * <p>Not two. A step that writes and then fails to record has run and does not know it; a step that
 * records and then fails to write has not run and believes it has. Committing them together makes both
 * impossible, and recording afterwards in a second transaction buys nothing — its failure window leaves
 * exactly the second case.
 *
 * <p>{@link ApplicationReadyEvent} rather than wiring, because Flyway has to have run first — including
 * {@code jmouse-access-jpa}'s own migrations, which is where the tables a step writes into come from.
 * {@link Ordered#HIGHEST_PRECEDENCE} so that anything else listening for the same event judges an
 * installation the seed has already reached.
 */
@Component
public class BootstrapLedger {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapLedger.class);

    /** Nothing here is a person, and pretending otherwise would make the column useless. */
    private static final String APPLIED_BY = "bootstrap-ledger";

    private final ObjectProvider<BootstrapStep> steps;
    private final BootstrapRecordRepository     records;
    private final TransactionTemplate           transactions;

    public BootstrapLedger(
            ObjectProvider<BootstrapStep> steps,
            BootstrapRecordRepository     records,
            PlatformTransactionManager    transactionManager) {

        this.steps        = steps;
        this.records      = records;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void runWhateverHasNotRun() {
        steps.orderedStream().forEach(this::considerOne);
    }

    private void considerOne(BootstrapStep step) {
        Optional<BootstrapRecord> already = records.findByStepKey(step.key());

        if (already.isPresent()) {
            if (already.get().getChecksum().equals(step.checksum())) {
                return;
            }

            reapply(step, already.get());
            return;
        }

        BootstrapStep.Outcome outcome = transactions.execute(status -> {
            BootstrapStep.Outcome applied = step.apply();
            records.save(recordOf(step, applied));

            return applied;
        });

        LOGGER.info("Bootstrap step '{}' applied — {} ({} rows)",
                step.key(), outcome.summary(), outcome.rowsWritten());
    }

    /**
     * ⚠️ <strong>A step whose source changed runs again, and that is what makes the policy a file
     * anybody may edit.</strong>
     *
     * <p>Innoventa refuses the boot here instead, and it is right to: its ledger covers seeds whose
     * re-run would overwrite an installation's own data, so it cannot tell a correction from a
     * divergence. Tessera's one step writes roles and bundles, and re-running it means exactly
     * <em>bring the tables back in line with the document</em> — which is the intended way to change what
     * a role carries in the source.
     *
     * <p>⚠️ The cost is stated rather than hidden: <strong>an edit made through the management screen to
     * a role the document also declares is undone by the next start whose document has moved.</strong>
     * The screen has to say so. Nothing else is touched — an assignment, a personal allow or deny, and a
     * role the document does not declare all survive, because the step only ever writes what the file
     * states.
     */
    private void reapply(BootstrapStep step, BootstrapRecord record) {
        LOGGER.info("Bootstrap step '{}' has run and its source has moved ({} → {}) — re-applying, "
                    + "which brings the tables back in line with the document.",
                step.key(), record.getChecksum(), step.checksum());

        BootstrapStep.Outcome outcome = transactions.execute(status -> {
            BootstrapStep.Outcome applied = step.apply();

            record.setChecksum(step.checksum());
            record.setAppliedAt(LocalDateTime.now());
            record.setNote(noteOf(step, applied));
            records.save(record);

            return applied;
        });

        LOGGER.info("Bootstrap step '{}' re-applied — {} ({} rows)",
                step.key(), outcome.summary(), outcome.rowsWritten());
    }

    private BootstrapRecord recordOf(BootstrapStep step, BootstrapStep.Outcome outcome) {
        return BootstrapRecord.builder()
                .id(UUID.randomUUID().toString())
                .stepKey(step.key())
                .checksum(step.checksum())
                .appliedAt(LocalDateTime.now())
                .appliedBy(APPLIED_BY)
                .note(noteOf(step, outcome))
                .build();
    }

    /**
     * What the step was for, and what it turned out to do.
     *
     * <p>Both, because the first is written before anybody knows the second, and reading the table a year
     * later the interesting question is usually whether the step actually wrote anything.
     */
    private String noteOf(BootstrapStep step, BootstrapStep.Outcome outcome) {
        String note = step.note() + " — " + outcome.summary();

        return note.length() <= 512 ? note : note.substring(0, 512);
    }
}
