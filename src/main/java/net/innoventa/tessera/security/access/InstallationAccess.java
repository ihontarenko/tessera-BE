package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import org.jmouse.access.AccessTarget;
import org.jmouse.access.EffectivePermissionsResolver;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * What the engine says about a caller when the question is aimed at <strong>no project in
 * particular</strong>.
 *
 * <p>{@link ProjectAccess} answers the same question at a project, and the split is not ceremony: a
 * target carrying a project resolves through the covering chain and can be answered by a grant held
 * anywhere above it, while this one names the installation and nothing else. A route about the shared
 * catalogs has no project to name, so asking {@code ProjectAccess} would mean inventing one.
 *
 * <p>⚠️ <strong>Only the shell reads this, and it is never the authority.</strong> The sidebar has to
 * decide whether to render an Administration entry before the caller clicks anything, which is the one
 * case where the interface needs the answer in advance. Every route it leads to declares
 * {@code @RequiresAccess(… scope = GLOBAL)} and refuses independently — this exists so the interface
 * stops offering what the server is about to refuse, not so the server can stop asking.
 */
@Component
@RequiredArgsConstructor
public class InstallationAccess {

    private final EffectivePermissionsResolver effectivePermissions;

    /** Every permission this member holds installation-wide. */
    public Set<String> permissionsOf(Member member) {
        return effectivePermissions
                .resolve(CurrentMemberSubject.of(member), AccessTarget.installation())
                .names();
    }

    public boolean holds(Member member, String permission) {
        return permissionsOf(member).contains(permission);
    }

}
