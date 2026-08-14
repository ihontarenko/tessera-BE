package net.innoventa.tessera.dto.assistant;

import java.util.List;
import java.util.Map;

/**
 * What came back from one turn.
 *
 * @param answer       what to show the person
 * @param messages     the conversation to send back with their next question
 * @param finished     false when the assistant ran out of rounds or tokens mid-task — the answer is
 *                     then a description of where it stopped rather than a result, and a screen should
 *                     say so rather than presenting it as complete
 * @param toolCalls    how many actions it took, so a screen can say "looked at 3 things"
 * @param inputTokens  what this turn cost, visible rather than discovered
 * @param outputTokens the other half of the same
 */
public record AssistantResponse(
    String                    answer,
    List<Map<String, Object>> messages,
    boolean                   finished,
    int                       toolCalls,
    long                      inputTokens,
    long                      outputTokens
) {
}
