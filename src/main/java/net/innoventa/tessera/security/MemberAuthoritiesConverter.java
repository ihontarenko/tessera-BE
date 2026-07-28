package net.innoventa.tessera.security;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.SystemRole;
import net.innoventa.tessera.repository.MemberRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Bridges Identity's JWTs — which carry no Tessera-specific claims, since Identity centralizes
 * authentication only — with the caller's local {@link net.innoventa.tessera.domain.Member} row,
 * granting a single {@code ROLE_ADMIN} / {@code ROLE_USER} authority off {@code systemRole}. This is
 * the lightweight <em>global</em> tier only; project-scoped permissions are resolved per action by
 * {@code ProjectPermissionService}, not carried as request-wide authorities (a member can hold
 * different permissions in each of their projects, so they cannot be flattened onto the principal).
 * <p>
 * Read-only, exactly like Moneta's converter: a subject with no {@code Member} row yet (first request,
 * provisioned by the controller via {@code MemberService.resolveMember}) is treated as a plain
 * {@code USER} for method security — Phase 1 gates nothing an unprovisioned first caller can reach on
 * {@code ADMIN}.
 */
@Component
@RequiredArgsConstructor
public class MemberAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final MemberRepository memberRepository;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        SystemRole systemRole = memberRepository.findBySubject(jwt.getSubject())
            .map(net.innoventa.tessera.domain.Member::getSystemRole)
            .orElse(SystemRole.USER);

        return List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.name()));
    }

}
