package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.issue.IssueReferenceRequest;
import net.innoventa.tessera.dto.issue.IssueReferenceView;
import net.innoventa.tessera.dto.issue.IssueRegisterResponse;
import net.innoventa.tessera.dto.issue.IssueSearchResponse;
import net.innoventa.tessera.service.IssueReferenceService;
import net.innoventa.tessera.service.IssueRegisterService;
import net.innoventa.tessera.service.IssueSearchService;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The cross-project issue search (ticket 10) — the one issue read whose scope is the caller rather than
 * a project, which is why it has a controller of its own rather than a tenth method on
 * {@link IssueController}, where every route hangs off a project or an issue.
 *
 * <p>⚠️ <strong>A bare declaration, and the filtering is the authorization.</strong> The route takes an
 * optional {@code projectId} but is not <em>about</em> one — its subject is "everything I can see" — so
 * there is no place to be refused at. What confines the answer is the visibility scope: the search runs
 * over the projects this member may browse and no others, which is the same fact the permission axis
 * would have used, applied as a filter instead of as a refusal.
 */
@RestController
@RequiredArgsConstructor
@RequiresAccess
public class IssueSearchController {

    private final IssueSearchService    issueSearchService;
    private final IssueRegisterService  issueRegisterService;
    private final IssueReferenceService issueReferenceService;

    /**
     * Every issue key one document mentions, resolved at once.
     *
     * <p>A `TES-42` written in a description or a comment renders as a live link, and this is what makes
     * it live. Bare {@code @RequiresAccess} for the reason the search above carries one: the request is
     * not <em>about</em> a project — it is about whatever the reader can see — so there is no place to
     * be refused at, and what confines the answer is the filtering.
     *
     * <p>⚠️ A key in a project the caller holds nothing at simply is not in the answer, exactly as a key
     * that does not exist is not. Telling the two apart would let anybody enumerate the tracker by
     * writing keys into a document.
     */
    @PostMapping("/api/issues/references")
    @RequiresAccess
    public List<IssueReferenceView> references(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody IssueReferenceRequest request
    ) {
        return issueReferenceService.resolve(jwt, request.issueKeys());
    }

    @GetMapping("/api/issues/search")
    public IssueSearchResponse search(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) String projectId,
        @RequestParam(required = false) String statusId,
        @RequestParam(required = false) String assigneeMemberId,
        @RequestParam(defaultValue = "false") boolean openOnly,
        @RequestParam(defaultValue = "false") boolean includeArchived,
        @RequestParam(name = "jmq:filter", required = false) String jmqFilter,
        @RequestParam(name = "jmq:order", required = false) String jmqOrder,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size
    ) {
        return issueSearchService.search(jwt, text, projectId, statusId, assigneeMemberId, openOnly,
            includeArchived, jmqFilter, jmqOrder, page, size);
    }

    /**
     * The registers — which efforts are being tracked, and what each one gathers (TSSR-45).
     *
     * <p>Here rather than under {@code /api/issues/{id}} for the reason the search above is: its subject is
     * the caller, not one issue. Bare {@code @RequiresAccess} for the same reason too — there is no place
     * to be refused at, and what confines the answer is the filtering.
     *
     * <p>⚠️ {@code linkTypeId} is <em>optional and never a name</em>. Which type means "gathers an effort"
     * is the interface's default and the reader's choice; naming one here would be TSSR-40's mistake with a
     * fresh coat of paint.
     */
    @GetMapping("/api/issues/registers")
    public IssueRegisterResponse registers(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String linkTypeId,
        /**
         * Which end of the arrow these issues are on — {@code false} the ones that gather, {@code true} the
         * ones gathered. ⚠️ Never both: one link read from both ends is one issue listed twice.
         */
        @RequestParam(defaultValue = "false") boolean inward,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return issueRegisterService.registers(jwt, linkTypeId, inward, page, size);
    }

}
