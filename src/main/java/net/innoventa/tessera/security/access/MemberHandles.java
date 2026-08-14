package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.repository.MemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * What a person written down in a policy document actually is.
 *
 * <h2>⚠️ The failure this exists to end, which really happened</h2>
 *
 * <p>{@code policy/tessera.jmp} said {@code assign subject ${tessera.bootstrap.owner}} and the property
 * said {@code SU}. The seed wrote a row with {@code subject_id = 'SU'}, the boot logged nothing wrong,
 * and the grant <strong>conferred absolutely nothing</strong> — because every grant in this installation
 * is keyed on a {@link Member}'s identifier and {@code SU} is the identity provider's subject claim. A
 * grant that parses, projects, and decides nothing is the exact failure writing authorization down is
 * supposed to prevent, arriving through the one line nobody checks.
 *
 * <p>Innoventa avoids it by writing raw identifiers into its document and saying so in a comment. That
 * works because its accounts are seeded by a migration whose keys are fixed literals. Tessera's members
 * arrive from Identity on first sign-in, so nobody knows an identifier before somebody signs in — which
 * makes "write the identifier" advice nobody can follow.
 *
 * <p>So a handle is resolved instead, and the order is deliberately most-specific first:
 *
 * <ol>
 *   <li><strong>The member identifier</strong>, for a document written after the fact.
 *   <li><strong>The identity-provider subject</strong> — {@code SU}, or whatever {@code sub} carries.
 *       This is the one a person can know in advance, because it is what they sign in as.
 *   <li><strong>The email</strong>, because it is what a person would try next.
 * </ol>
 *
 * <p>⚠️ <strong>It answers empty rather than guessing.</strong> A handle matching nothing is a real
 * state — the owner has not signed in yet — and the callers say so out loud rather than writing a row
 * that looks like a grant.
 */
@Component
@RequiredArgsConstructor
public class MemberHandles {

    private final MemberRepository members;

    /** The member this handle names, or empty where nobody answers to it yet. */
    @Transactional(readOnly = true)
    public Optional<Member> resolve(String handle) {
        if (handle == null || handle.isBlank()) {
            return Optional.empty();
        }

        return members.findById(handle)
                .or(() -> members.findBySubject(handle))
                .or(() -> members.findByEmail(handle));
    }

    /** Whether this member is the one the handle names — asked when a member is created. */
    public boolean names(Member member, String handle) {
        if (member == null || handle == null || handle.isBlank()) {
            return false;
        }

        return handle.equals(member.getId())
               || handle.equals(member.getSubject())
               || handle.equals(member.getEmail());
    }
}
