package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.SystemRole;
import net.innoventa.tessera.repository.MemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Inserts a brand-new {@link Member} in its <strong>own</strong> transaction so that a unique-constraint
 * collision under concurrent first calls rolls back only this insert — leaving the caller's transaction
 * intact to recover by re-reading the winner's row. A same-transaction catch-and-refind cannot do this:
 * once a constraint violation fires, Spring marks the surrounding transaction rollback-only and any
 * further query in it fails. Kept a separate bean (not a self-invoked method) so {@code REQUIRES_NEW}
 * actually goes through the proxy.
 */
@Component
@RequiredArgsConstructor
public class MemberProvisioner {

    private final MemberRepository memberRepository;
    private final Supplier<String> idGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member provision(String subject, String displayName, String email) {
        return memberRepository.save(Member.builder()
            .id(idGenerator.get())
            .subject(subject)
            .displayName(displayName)
            .email(email)
            .systemRole(SystemRole.USER)
            .build());
    }

}
