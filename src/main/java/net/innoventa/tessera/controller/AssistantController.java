package net.innoventa.tessera.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.ai.AssistantService;
import net.innoventa.tessera.dto.assistant.AssistantRequest;
import net.innoventa.tessera.dto.assistant.AssistantResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The whole of the assistant's HTTP surface: ask, and find out whether asking is possible.
 *
 * <p>⚠️ <strong>No permission of its own, and that is deliberate here.</strong> Every action the
 * assistant can take is already gated per project by Tessera's own model — a person who belongs to no
 * project gets an assistant that can reach nothing. Innoventa gates the conversation separately
 * because a message costs provider tokens against an installation budget; when Tessera has a bill to
 * protect, the same permission belongs here and this comment is the note to add it.
 *
 * <p>⚠️ <strong>No streaming.</strong> A turn may take several rounds, and deciding what a
 * half-finished workflow refusal looks like mid-stream is a design question this does not answer yet.
 */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/ask")
    public AssistantResponse ask(@Valid @RequestBody AssistantRequest request) {
        return assistantService.answer(request);
    }

    /**
     * Whether this installation has a model configured.
     *
     * <p>So the screen can be absent rather than present and broken: a chat box answering every
     * message with the same configuration error is worse than no chat box.
     */
    @GetMapping("/availability")
    public Map<String, Object> availability() {
        return Map.of("available", assistantService.isAvailable());
    }
}
