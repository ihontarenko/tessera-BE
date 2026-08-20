package net.innoventa.tessera.service.file;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.files.OwnerReference;
import org.jmouse.files.management.FileManagementContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Where an attachment's bytes go, and who is putting them there.
 *
 * <h2>⚠️ Both answers are the server's, and one of them is load-bearing</h2>
 *
 * <p>The uploader is read from the request's own authentication, never from what the request said.
 * {@code ManagedFileAccessTargetResolver} reads it back to answer <em>whose file is this</em>, which is
 * how a permission held at {@code SELF} is honoured — so a client able to write that field would be a
 * client able to claim ownership of anybody's attachment.</p>
 *
 * <p>The namespace is one value because a tracker files one kind of file. It is a <strong>storage-key
 * prefix, not a directory</strong>: Tessera runs the library's file surface with its tree switched off,
 * and this is the only sense in which an attachment has a location.</p>
 */
@Component
@RequiredArgsConstructor
public class AttachmentFileContext implements FileManagementContext {

    private final MemberService memberService;

    @Override
    public String namespaceFor(OwnerReference owner) {
        return AttachmentOwners.NAMESPACE;
    }

    @Override
    public String uploader() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // No request behind it — a background job, a test. Nobody is a truthful answer; a placeholder
        // would be a name the issue screen then has to pretend it can resolve.
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        return memberService.resolveMember(jwt).getId();
    }
}
