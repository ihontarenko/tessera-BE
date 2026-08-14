package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.EstimationScheme;
import net.innoventa.tessera.domain.EstimationSchemeItem;
import net.innoventa.tessera.dto.configuration.EstimationSchemeRequest;
import net.innoventa.tessera.dto.configuration.EstimationSchemeResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.EstimationSchemeItemRepository;
import net.innoventa.tessera.repository.EstimationSchemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

/**
 * Writing estimation scales — the third scheme kind, and the one that needed no new mechanism.
 *
 * <p>⚠️ <strong>Custom is not a feature; it is what "an estimation scheme is a catalog entity" already
 * means (ADR-0001).</strong> Fibonacci and T-shirt ship as seeded rows, and an administrator building a
 * fifth scale of their own is the same create as any other — which is why there is no {@code CUSTOM}
 * discriminator anywhere in this file.
 *
 * <p>⚠️ <strong>The last-scheme rule does not apply here</strong>, unlike the other two kinds. An
 * installation with no estimation schemes is coherent and merely means nobody estimates; an
 * installation with no issue-type schemes is a tracker whose next project cannot be created. The
 * absence is deliberate rather than an omission to tidy up later.
 *
 * <p>⚠️ <strong>Changing a scale rewrites no estimate.</strong> A stored number stays the number it was
 * — see {@link net.innoventa.tessera.domain.EstimationSchemeItem} — so an issue whose weight no longer
 * matches any item renders as the raw number rather than losing its estimate. That is the documented
 * cost of storing the weight, and the reason burndown and velocity never noticed the change.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EstimationSchemeWriteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EstimationSchemeWriteService.class);

    private static final String KIND = "estimation scheme";

    private final EstimationSchemeRepository     estimationSchemeRepository;
    private final EstimationSchemeItemRepository estimationSchemeItemRepository;
    private final ConfigurationUsage             configurationUsage;
    private final Supplier<String>               idGenerator;

    @Transactional(readOnly = true)
    public List<EstimationSchemeResponse> list() {
        return estimationSchemeRepository.findAllByOrderByNameAsc().stream()
            .map(scheme -> toResponse(scheme, itemsOf(scheme.getId())))
            .toList();
    }

    public EstimationSchemeResponse create(EstimationSchemeRequest request) {
        String name = SchemeRules.requireName(request.name(), KIND);
        SchemeRules.requireNameAvailable(estimationSchemeRepository.existsByNameIgnoreCase(name), KIND, name);
        requireDistinctLabels(request.items());

        EstimationScheme scheme = estimationSchemeRepository.save(EstimationScheme.builder()
            .id(idGenerator.get())
            .name(name)
            .description(request.description())
            .build());

        replaceItems(scheme.getId(), request.items());

        LOGGER.info("Estimation scheme '{}' created with {} options — no project estimates on it until "
                    + "one is pointed at it", name, request.items().size());

        return toResponse(scheme, itemsOf(scheme.getId()));
    }

    public EstimationSchemeResponse update(String schemeId, EstimationSchemeRequest request) {
        EstimationScheme scheme = requireScheme(schemeId);
        String name = SchemeRules.requireName(request.name(), KIND);
        SchemeRules.requireNameAvailable(
            estimationSchemeRepository.existsByNameIgnoreCaseAndIdNot(name, schemeId), KIND, name);
        requireDistinctLabels(request.items());

        scheme.setName(name);
        scheme.setDescription(request.description());
        replaceItems(schemeId, request.items());

        LOGGER.info("Estimation scheme '{}' rewritten — every estimate already stored keeps its number, "
                    + "and any that no longer matches an option shows as that number", name);

        return toResponse(scheme, itemsOf(schemeId));
    }

    /**
     * ⚠️ Refused while a project estimates on it or while it is the instance default — and never for
     * being the last one, which is the difference from the other two kinds.
     */
    public void delete(String schemeId) {
        EstimationScheme scheme = requireScheme(schemeId);

        CatalogRules.requireNothingHoldsIt(
            configurationUsage.ofEstimationScheme(schemeId), KIND, scheme.getName());

        estimationSchemeItemRepository.deleteAll(itemsOf(schemeId));
        estimationSchemeRepository.delete(scheme);

        LOGGER.info("Estimation scheme '{}' deleted", scheme.getName());
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * ⚠️ Labels are unique within a scale, weights are not.
     *
     * <p>Two options called {@code L} is a picker with two identical rows, and the unique constraint's
     * 500 rather than a sentence. Two options <em>weighing</em> the same is fine — the reverse lookup
     * takes the first in order, which is a documented consequence rather than a bug.
     */
    private static void requireDistinctLabels(List<EstimationSchemeRequest.Item> items) {
        long distinct = items.stream()
            .map(item -> item.label().trim().toLowerCase())
            .distinct()
            .count();

        if (distinct != items.size()) {
            throw new BusinessRuleViolationException(
                "Two options on this scale have the same label. A picker cannot offer the same word "
                + "twice — weights may repeat, labels may not.");
        }
    }

    /** ⚠️ Replaced, not diffed — see {@code IssueTypeSchemeWriteService.replaceItems}. */
    private void replaceItems(String schemeId, List<EstimationSchemeRequest.Item> items) {
        estimationSchemeItemRepository.deleteAll(itemsOf(schemeId));
        estimationSchemeItemRepository.flush();

        for (int sequence = 0; sequence < items.size(); sequence++) {
            EstimationSchemeRequest.Item item = items.get(sequence);

            estimationSchemeItemRepository.save(EstimationSchemeItem.builder()
                .id(idGenerator.get())
                .schemeId(schemeId)
                .label(item.label().trim())
                .weight(item.weight())
                .sequence(sequence)
                .build());
        }
    }

    private List<EstimationSchemeItem> itemsOf(String schemeId) {
        return estimationSchemeItemRepository.findBySchemeIdOrderBySequenceAsc(schemeId);
    }

    private EstimationScheme requireScheme(String schemeId) {
        return estimationSchemeRepository.findById(schemeId)
            .orElseThrow(() -> new ResourceNotFoundException("Estimation scheme not found: " + schemeId));
    }

    static EstimationSchemeResponse toResponse(EstimationScheme scheme, List<EstimationSchemeItem> items) {
        return new EstimationSchemeResponse(
            scheme.getId(),
            scheme.getName(),
            scheme.getDescription(),
            items.stream()
                .map(item -> new EstimationSchemeResponse.Item(item.getLabel(), item.getWeight()))
                .toList());
    }

}
