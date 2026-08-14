package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Status;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.StatusRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Looking a status up, and refusing in one voice when there is none.
 *
 * <p>Small on purpose. Every write service in this package needs the same two reads — "this one, or a
 * 404" and "these, by id, for the messages" — and each had grown its own copy, which is how two
 * screens come to phrase the same absence two ways.
 */
@Component
@RequiredArgsConstructor
public class Statuses {

    private final StatusRepository statusRepository;

    @Transactional(readOnly = true)
    public Status require(String statusId) {
        return statusRepository.findById(statusId)
            .orElseThrow(() -> new ResourceNotFoundException("Status not found: " + statusId));
    }

    /** Status id → name, for the sentences a rule has to phrase itself in. */
    @Transactional(readOnly = true)
    public Map<String, String> namesOf(Collection<String> statusIds) {
        return statusRepository.findAllById(statusIds).stream()
            .collect(Collectors.toMap(Status::getId, Status::getName));
    }

    @Transactional(readOnly = true)
    public Map<String, Status> byId(Collection<String> statusIds) {
        return statusRepository.findAllById(statusIds).stream()
            .collect(Collectors.toMap(Status::getId, Function.identity()));
    }

}
