package net.innoventa.tessera.ai.authorization;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.repository.MemberRepository;
import org.jmouse.ai.mcp.authorization.server.CredentialOwners;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Who an agent acts for, in Tessera's words — a {@link Member}.
 *
 * <h2>⚠️ This is all that is left of a 366-line class</h2>
 *
 * <p>Everything else `McpCredentialService` did is `AgentCredentials` now, in
 * `jmouse-ai-mcp-authorization`. It was written here, in WiQi and in Identity, and the three agreed on
 * every decision that mattered — the claims, the rotation, the sliding window, the four refusals. The one
 * genuine difference was this lookup.
 *
 * <p>⚠️ <strong>By member id, never through {@code members.parent_id}.</strong> An agent's member row
 * (`TSSR-32`) carries a parent pointing at the same person, and reading <em>that</em> to decide what a
 * credential may do is exactly the read `ADR-0021` forbids. The reference handed in is the authoritative
 * one; the mirror is for by-lines.
 */
@Component
@RequiredArgsConstructor
public class MemberCredentialOwners implements CredentialOwners {

    private final MemberRepository members;

    @Override
    public Optional<CredentialOwner> find(String ownerReference) {
        return members.findById(ownerReference)
                .map(member -> new CredentialOwner(
                        member.getSubject(), member.getDisplayName(), member.getEmail()));
    }
}
