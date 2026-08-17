package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.assistant.AssistantRequest;
import net.innoventa.tessera.dto.assistant.AssistantResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import org.jmouse.ai.conversation.ConversationRequest;
import org.jmouse.ai.conversation.ConversationResult;
import org.jmouse.ai.conversation.ConversationRunner;
import org.jmouse.ai.preferences.AiPreferences;
import org.jmouse.ai.view.ProviderRegistry;
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

    private final ConversationRunner runner;

    /**
     * ⚠️ <strong>What decides whether there is an assistant, now that the runner always exists.</strong>
     * The provider is a row rather than a property, so the model is resolved per turn and the bean is
     * built unconditionally — which means its presence stopped being the answer to "is the assistant
     * on". This port is the answer: it reports what the settings source actually resolved, and it is
     * empty when nothing is in force.
     */
    private final ProviderRegistry providers;

    /** Where the system prompt actually comes from — see {@link AssistantPrompt}. */
    private final AiPreferences preferences;

    public AssistantResponse answer(AssistantRequest request) {
        requireProvider();

        // ⚠️ Read per turn, not held from startup: an installation that rewrites the prompt on the
        // administration screen means the next question, not the next deploy. It is one indexed row,
        // and the shipped wording answers where nobody has rewritten anything.
        ConversationResult result = runner.run(
                ConversationRequest.continuing(request.messages())
                        .withSystem(preferences.value(AssistantPrompt.NAME))
                        .asking(request.question()));

        return new AssistantResponse(
                result.describe(),
                result.messages(),
                result.finished(),
                result.toolCalls(),
                result.usage().inputTokens(),
                result.usage().outputTokens());
    }

    /**
     * Whether this installation has a usable model, so a screen can be absent rather than broken.
     *
     * <p>⚠️ <strong>A model <em>and</em> a key.</strong> A configuration with no credential resolves
     * perfectly and would be refused before anything was sent, so it does not count as available — a
     * chat box answering every message with the same authentication error is worse than no chat box.
     */
    public boolean isAvailable() {
        return providers.active().filter(ProviderRegistry.ActiveProvider::usable).isPresent();
    }

    private void requireProvider() {
        if (isAvailable()) {
            return;
        }

        throw new BusinessRuleViolationException(
                "This installation has no model in force, so the assistant cannot answer. Add a provider "
                + "configuration under Administration → AI and put it in force.");
    }
}
