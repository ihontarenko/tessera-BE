package net.innoventa.tessera.service.file;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.ActivityLogService;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.files.OwnerReference;
import org.jmouse.files.management.FileManagementEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Attaching a file to an issue is a thing that happened to the issue, so the issue's history says so.
 *
 * <h2>⚠️ A listener, and that is the whole point of it being one</h2>
 *
 * <p>The route that takes the upload is the library's, and it stays the library's. The alternative —
 * a Tessera controller wrapping it so there is somewhere to put this — is precisely the duplication the
 * shared file surface exists to delete, and it would arrive looking reasonable. So the library says
 * <em>what happened</em> ({@link FileManagementEvent}) and this says what it means here.</p>
 *
 * <p>⚠️ <strong>Only {@code ISSUE} bindings.</strong> Tessera files attachments against issues and
 * nothing else today, and a listener that assumed every managed file were one would write history rows
 * against an identifier that is not an issue's the moment that stops being true.</p>
 *
 * <h2>⚠️ The actor is the request's, then the row's — and the second one is not a nicety</h2>
 *
 * <p>The request is asked first: {@code uploadedBy} is who put the bytes there <em>originally</em>, and
 * under content-addressed keys that can be somebody else entirely — two people attaching the same
 * screenshot share one stored object, and only the request knows which of them is acting now.</p>
 *
 * <p>⚠️ But <strong>a protocol call has no request at all.</strong> A tool invocation runs on a thread
 * with neither a {@code SecurityContext} nor a request scope, so asking only the context would drop the
 * history entry for <em>every</em> attachment an agent ever makes — silently, which is the worst way for
 * an audit trail to be wrong. {@code issues_attach} sets {@code uploadedBy} to the acting member itself,
 * so the row is the answer when the thread cannot be.</p>
 *
 * <p>With neither — a sweep, a test, a future background job — nothing is logged rather than the act
 * being attributed to nobody. An activity row with an empty actor is worse than an absent one: it reads
 * as a person the screen then cannot name.</p>
 */
@Component
@RequiredArgsConstructor
public class AttachmentActivityListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentActivityListener.class);

    /** What the history screen calls the thing that changed. */
    private static final String FIELD_ATTACHMENT = "attachment";

    private final ActivityLogService activityLogService;
    private final MemberService      memberService;

    /**
     * A file was attached.
     *
     * @param uploaded what the library announced
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUploaded(FileManagementEvent.Uploaded uploaded) {
        // ⚠️ The uploader on the ROW is the fallback, and it is what makes an agent's attachment appear
        // in the history at all: a tool call has no request behind it and therefore no security context,
        // so asking only the context would drop the entry silently for every protocol upload. The row's
        // `uploadedBy` was set by whoever did the upload — the browser's member, or the agent's.
        record(uploaded.owner(), uploaded.file().getUploadedBy(),
               actor -> activityLogService.record(uploaded.owner().ownerId(), actor,
                   activityLogService.changeSet()
                       .added(FIELD_ATTACHMENT, uploaded.file().getDisplayName())));
    }

    /**
     * A file was removed — from every issue it was on, which is normally exactly one.
     *
     * @param deleted what the library announced
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onDeleted(FileManagementEvent.Deleted deleted) {
        for (OwnerReference owner : deleted.owners()) {
            // ⚠️ No row left to read an uploader off — it is already gone. A protocol delete with no request
            // behind it therefore records nothing, which is honest: an entry with no actor reads as a
            // person the screen then cannot name.
            record(owner, null,
                   actor -> activityLogService.record(owner.ownerId(), actor,
                       activityLogService.changeSet()
                           .removed(FIELD_ATTACHMENT, deleted.displayName())));
        }
    }

    /**
     * A file moved to another issue — two entries, because it left one history and joined another.
     *
     * @param refiled what the library announced
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onRefiled(FileManagementEvent.Refiled refiled) {
        String name = refiled.file().getDisplayName();

        if (refiled.from() != null) {
            record(refiled.from(), refiled.file().getUploadedBy(),
                   actor -> activityLogService.record(refiled.from().ownerId(), actor,
                       activityLogService.changeSet().removed(FIELD_ATTACHMENT, name)));
        }

        record(refiled.to(), refiled.file().getUploadedBy(),
               actor -> activityLogService.record(refiled.to().ownerId(), actor,
                   activityLogService.changeSet().added(FIELD_ATTACHMENT, name)));
    }

    /**
     * Runs the entry against an issue owner, where there is one and somebody to attribute it to.
     */
    private void record(OwnerReference owner, String fallbackActor, java.util.function.Consumer<String> entry) {
        if (!AttachmentOwners.ISSUE.equals(owner.ownerType())) {
            return;
        }

        Optional<String> actor = actingMemberId().or(() -> Optional.ofNullable(fallbackActor));

        if (actor.isEmpty()) {
            LOGGER.debug("No caller behind an attachment change on issue {} — nothing logged",
                         owner.ownerId());

            return;
        }

        entry.accept(actor.get());
    }

    /**
     * Who is asking, where a request is asking at all.
     */
    private Optional<String> actingMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        return Optional.of(memberService.resolveMember(jwt)).map(Member::getId);
    }
}
