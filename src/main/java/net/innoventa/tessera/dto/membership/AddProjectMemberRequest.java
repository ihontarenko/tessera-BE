package net.innoventa.tessera.dto.membership;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Add a member to a project with one or more roles (which combine additively). */
public record AddProjectMemberRequest(
    @NotBlank String memberId,
    @NotEmpty List<String> roleIds
) {
}
