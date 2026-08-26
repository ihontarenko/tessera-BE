package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Issue;
import net.innoventa.tessera.repository.IssueRepository;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Draws the permanent identifier an issue keeps for good.
 *
 * <p>Its own class rather than four lines inside {@code IssueService}, because it is the one place a
 * hash is made and that is worth being able to point at. See {@code Issue.hash} for why an issue needs
 * an identifier its key cannot be.
 *
 * <h2>⚠️ Probed, not assumed unique</h2>
 *
 * <p>Six hex characters is about 16.7 million and a collision is unlikely rather than impossible — and
 * the hash is what every stored reference resolves through, so two issues sharing one is two issues at
 * one address. Worse, the unique index would refuse the second <em>issue</em>, not merely the second
 * hash: somebody would be told their ticket could not be raised, for a reason nothing on the screen
 * could explain. A handful of extra draws costs nothing beside that.
 */
@Service
@RequiredArgsConstructor
public class IssueHashes {

    /**
     * How many times to draw before giving up.
     *
     * <p>Reaching this means the table is dense enough that six characters is the wrong length, not that
     * this draw was unlucky — so it throws rather than widening silently, and the fix is
     * {@link Issue#HASH_LENGTH} plus a migration.
     */
    private static final int ATTEMPTS = 100;

    private final IssueRepository  issues;
    private final Supplier<String> idGenerator;

    /** A hash no issue holds yet. */
    public String draw() {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String candidate = shortId();

            if (!issues.existsByHash(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Could not draw an issue hash nothing already holds.");
    }

    /**
     * ⚠️ From the same generator every identifier in this application comes from, rather than a
     * {@code Random} of its own — one source of randomness is one thing to make deterministic.
     *
     * <p>The head of a UUID rather than a digest of the summary or the key: a reference must not be
     * derivable from what an issue is called, or two projects raising the same ticket on the same day
     * collide by construction instead of by chance — and a hash that follows the key would move when the
     * key does, which is the entire thing this is here to avoid.
     */
    private String shortId() {
        String generated = idGenerator.get().replace("-", "");

        return generated.substring(0, Math.min(Issue.HASH_LENGTH, generated.length()));
    }

}
