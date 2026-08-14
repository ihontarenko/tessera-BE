package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A whole issue-type scheme, sent as one.
 *
 * <p>⚠️ <strong>Membership, order and default arrive together on purpose.</strong> Split into
 * add / remove / reorder / set-default routes, the one rule that matters — the default must be one of
 * the members — is unenforceable in the middle: removing the default type has to either be refused
 * (and then adding a replacement first is a dance) or leave a scheme in a state no rule describes.
 * Sent whole, the rule is a sentence about one payload.
 *
 * @param issueTypeIds the members, <strong>in the order the pickers offer them</strong> — position is
 *                     the ordering, so there is no separate sequence to keep in step
 */
public record IssueTypeSchemeRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 255) String description,
    @NotBlank String defaultIssueTypeId,
    @NotEmpty List<String> issueTypeIds
) {
}
