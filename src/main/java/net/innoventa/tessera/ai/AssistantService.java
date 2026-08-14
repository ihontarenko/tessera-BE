package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.assistant.AssistantRequest;
import net.innoventa.tessera.dto.assistant.AssistantResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import org.jmouse.ai.conversation.ConversationRequest;
import org.jmouse.ai.conversation.ConversationResult;
import org.jmouse.ai.conversation.ConversationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * One turn of a conversation with the person whose work it is.
 *
 * <p><strong>The size of this class is the point of ticket 18.</strong> Tessera contributed nothing to
 * the library's design, and what it had to write to get an assistant is: three tool definitions, three
 * seams, and this. Every permission check, the project confinement, the guards and the conversation
 * loop arrive without a line about any of them here.
 *
 * <p>⚠️ <strong>No protocol round trip.</strong> The actions are already in the catalogue; there is
 * nothing to connect to. Pointing a client at Tessera's own endpoint would mean HTTP to itself, a
 * second authentication, a second transaction and a permission gate evaluated against the wrong
 * principal.
 *
 * <p>The runner is stateless and the client holds the growing message array. Persisting a conversation
 * is a separate decision with a retention question behind it — a stored one contains whatever the
 * tools returned, which is somebody's issues copied into a second place with a second lifetime.
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    /**
     * ⚠️ Optional on purpose. The runner exists only where a provider is configured, and an
     * installation with tools and no model is a supported arrangement rather than a mistake.
     */
    private final ObjectProvider<ConversationRunner> runner;

    public AssistantResponse answer(AssistantRequest request) {
        ConversationResult result = requireRunner().run(
                ConversationRequest.continuing(request.messages())
                        .withSystem(AssistantPrompt.SYSTEM)
                        .asking(request.question()));

        return new AssistantResponse(
                result.describe(),
                result.messages(),
                result.finished(),
                result.toolCalls(),
                result.usage().inputTokens(),
                result.usage().outputTokens());
    }

    /** Whether this installation has a model at all, so a screen can be absent rather than broken. */
    public boolean isAvailable() {
        return runner.getIfAvailable() != null;
    }

    private ConversationRunner requireRunner() {
        ConversationRunner available = runner.getIfAvailable();

        if (available != null) {
            return available;
        }

        throw new BusinessRuleViolationException(
                "This installation has no model configured, so the assistant cannot answer. Set the "
                + "provider under 'jmouse.ai.provider' to switch it on.");
    }
}
