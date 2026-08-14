package net.innoventa.tessera.dto.assistant;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * One turn, carrying the conversation so far.
 *
 * <p>The client holds the history and the server holds none of it — see {@code AssistantService} for
 * why that is a decision rather than an omission.
 *
 * @param question what the person just asked
 * @param messages the conversation exactly as a previous answer returned it; empty to open a new one
 */
public record AssistantRequest(
    @NotBlank(message = "Ask something.")
    String                    question,
    List<Map<String, Object>> messages
) {

    public AssistantRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
