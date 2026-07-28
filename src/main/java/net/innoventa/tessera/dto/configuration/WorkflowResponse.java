package net.innoventa.tessera.dto.configuration;

import java.util.List;

public record WorkflowResponse(
    String id,
    String name,
    String description,
    List<TransitionResponse> transitions
) {
}
