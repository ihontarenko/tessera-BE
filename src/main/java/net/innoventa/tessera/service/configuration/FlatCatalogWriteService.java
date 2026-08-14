package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.LinkType;
import net.innoventa.tessera.domain.Priority;
import net.innoventa.tessera.domain.Resolution;
import net.innoventa.tessera.dto.configuration.LinkTypeRequest;
import net.innoventa.tessera.dto.configuration.PriorityRequest;
import net.innoventa.tessera.dto.configuration.PriorityResponse;
import net.innoventa.tessera.dto.configuration.ResolutionRequest;
import net.innoventa.tessera.dto.configuration.ResolutionResponse;
import net.innoventa.tessera.dto.link.LinkTypeResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.LinkTypeRepository;
import net.innoventa.tessera.repository.PriorityRepository;
import net.innoventa.tessera.repository.ResolutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Writing the three catalogs with no graph semantics — priorities, resolutions and link types.
 *
 * <p>They are together because they are one family: a row with a name, no edges, no scheme, and nothing
 * downstream that has to be recomputed when it changes. Statuses, workflows and issue types each carry a
 * meaning the engine reads, and each gets a service of its own for exactly that reason.
 *
 * <p>⚠️ <strong>This is where the write pattern for the whole cluster is set.</strong> Every later
 * catalog inherits the same four moves in the same order: name it, check the name is free, do the thing,
 * log a line. And the same two refusals — {@link ConfigurationUsage} answers what holds a row and
 * {@link CatalogRules} turns that into a {@code 409}, so no service invents its own account of why
 * something cannot go.
 *
 * <h2>⚠️ Editing is in place, and it is shared</h2>
 *
 * <p>These catalogs are global by design (ADR-0001): every project sees the same priorities. Renaming
 * one renames it everywhere, immediately, for everybody — there is no copy-on-write and no draft. What
 * is offered instead of safety-by-copying is knowing in advance: the rename impact is a read the screen
 * makes before it offers Save.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FlatCatalogWriteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlatCatalogWriteService.class);

    private final PriorityRepository   priorityRepository;
    private final ResolutionRepository resolutionRepository;
    private final LinkTypeRepository   linkTypeRepository;
    private final ConfigurationUsage   configurationUsage;
    private final Supplier<String>     idGenerator;

    // ── Priorities ────────────────────────────────────────────────────────────

    /** Appended to the end of the picker; where it belongs in the order is a separate, whole-list decision. */
    public PriorityResponse createPriority(PriorityRequest request) {
        String name = CatalogRules.requireName(request.name(), "priority");
        CatalogRules.requireNameAvailable(priorityRepository.existsByNameIgnoreCase(name), "priority", name);

        int sequence = priorityRepository.findFirstByOrderBySequenceDesc()
            .map(last -> last.getSequence() + 1)
            .orElse(1);

        Priority priority = priorityRepository.save(Priority.builder()
            .id(idGenerator.get())
            .name(name)
            .color(request.color())
            .sequence(sequence)
            .build());

        LOGGER.info("Priority '{}' created at position {}", name, sequence);

        return toResponse(priority);
    }

    public PriorityResponse updatePriority(String priorityId, PriorityRequest request) {
        Priority priority = requirePriority(priorityId);
        String name = CatalogRules.requireName(request.name(), "priority");
        CatalogRules.requireNameAvailable(
            priorityRepository.existsByNameIgnoreCaseAndIdNot(name, priorityId), "priority", name);

        LOGGER.info("Priority '{}' renamed to '{}'", priority.getName(), name);

        priority.setName(name);
        priority.setColor(request.color());

        return toResponse(priority);
    }

    public void deletePriority(String priorityId) {
        Priority priority = requirePriority(priorityId);

        CatalogRules.requireCatalogSurvives(priorityRepository.count() - 1, "priority",
            "an issue must be given one to be created, so a project with no priorities cannot raise one.");
        CatalogRules.requireNothingHoldsIt(
            configurationUsage.ofPriority(priorityId), "priority", priority.getName());

        priorityRepository.delete(priority);

        LOGGER.info("Priority '{}' deleted", priority.getName());
    }

    /** The whole order at once — see {@code ReorderRequest} for why a move would be the worse contract. */
    public List<PriorityResponse> reorderPriorities(List<String> orderedIds) {
        List<Priority> priorities = priorityRepository.findAllByOrderBySequenceAsc();

        requireSameRows(orderedIds, priorities.stream().map(Priority::getId).toList(), "priorities");

        for (int position = 0; position < orderedIds.size(); position++) {
            String priorityId = orderedIds.get(position);

            priorities.stream()
                .filter(candidate -> candidate.getId().equals(priorityId))
                .findFirst()
                .orElseThrow()
                .setSequence(position + 1);
        }

        LOGGER.info("Priorities reordered ({} rows)", orderedIds.size());

        return priorityRepository.findAllByOrderBySequenceAsc().stream().map(this::toResponse).toList();
    }

    // ── Resolutions ───────────────────────────────────────────────────────────

    public ResolutionResponse createResolution(ResolutionRequest request) {
        String name = CatalogRules.requireName(request.name(), "resolution");
        CatalogRules.requireNameAvailable(resolutionRepository.existsByNameIgnoreCase(name), "resolution", name);

        Resolution resolution = resolutionRepository.save(Resolution.builder()
            .id(idGenerator.get())
            .name(name)
            .description(request.description())
            .build());

        LOGGER.info("Resolution '{}' created", name);

        return toResponse(resolution);
    }

    public ResolutionResponse updateResolution(String resolutionId, ResolutionRequest request) {
        Resolution resolution = requireResolution(resolutionId);
        String name = CatalogRules.requireName(request.name(), "resolution");
        CatalogRules.requireNameAvailable(
            resolutionRepository.existsByNameIgnoreCaseAndIdNot(name, resolutionId), "resolution", name);

        LOGGER.info("Resolution '{}' renamed to '{}'", resolution.getName(), name);

        resolution.setName(name);
        resolution.setDescription(request.description());

        return toResponse(resolution);
    }

    /**
     * ⚠️ The last resolution is refused <strong>even when no issue holds one</strong>.
     *
     * <p>The failure it would cause is in the future rather than in the data: an issue is closed when its
     * resolution is set (ADR-0004), so the next transition into a Done-category status would have nothing
     * to offer and nothing to store. An empty catalog looks harmless right up until somebody finishes
     * something.
     */
    public void deleteResolution(String resolutionId) {
        Resolution resolution = requireResolution(resolutionId);

        CatalogRules.requireCatalogSurvives(resolutionRepository.count() - 1, "resolution",
            "closing an issue means setting one, so a transition into a Done status would have nothing "
            + "to offer.");
        CatalogRules.requireNothingHoldsIt(
            configurationUsage.ofResolution(resolutionId), "resolution", resolution.getName());

        resolutionRepository.delete(resolution);

        LOGGER.info("Resolution '{}' deleted", resolution.getName());
    }

    // ── Link types ────────────────────────────────────────────────────────────

    public LinkTypeResponse createLinkType(LinkTypeRequest request) {
        String name = CatalogRules.requireName(request.name(), "link type");
        CatalogRules.requireNameAvailable(linkTypeRepository.existsByNameIgnoreCase(name), "link type", name);

        LinkType linkType = linkTypeRepository.save(LinkType.builder()
            .id(idGenerator.get())
            .name(name)
            .outwardLabel(request.outwardLabel().trim())
            .inwardLabel(request.inwardLabel().trim())
            .build());

        LOGGER.info("Link type '{}' created ({} / {})", name, linkType.getOutwardLabel(), linkType.getInwardLabel());

        return LinkTypeResponse.from(linkType);
    }

    public LinkTypeResponse updateLinkType(String linkTypeId, LinkTypeRequest request) {
        LinkType linkType = requireLinkType(linkTypeId);
        String name = CatalogRules.requireName(request.name(), "link type");
        CatalogRules.requireNameAvailable(
            linkTypeRepository.existsByNameIgnoreCaseAndIdNot(name, linkTypeId), "link type", name);

        LOGGER.info("Link type '{}' renamed to '{}'", linkType.getName(), name);

        linkType.setName(name);
        linkType.setOutwardLabel(request.outwardLabel().trim());
        linkType.setInwardLabel(request.inwardLabel().trim());

        return LinkTypeResponse.from(linkType);
    }

    /**
     * ⚠️ <strong>No last-row rule here, and that is deliberate.</strong> A link is optional, so an
     * installation with no link types is coherent: it means nobody links issues, not that something is
     * broken.
     */
    public void deleteLinkType(String linkTypeId) {
        LinkType linkType = requireLinkType(linkTypeId);

        CatalogRules.requireNothingHoldsIt(
            configurationUsage.ofLinkType(linkTypeId), "link type", linkType.getName());

        linkTypeRepository.delete(linkType);

        LOGGER.info("Link type '{}' deleted", linkType.getName());
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * A reorder names every row exactly once, or it is a request that lost something on the way.
     *
     * <p>Patching a short list — appending whatever was missing — would silently reorder rows the caller
     * never mentioned, which is precisely the surprise a whole-list contract exists to avoid.
     */
    private static void requireSameRows(List<String> given, List<String> existing, String catalog) {
        Set<String> givenOnce = new HashSet<>(given);

        if (givenOnce.size() != given.size() || !givenOnce.equals(new HashSet<>(existing))) {
            throw new BusinessRuleViolationException(
                "The new order has to name every one of the " + existing.size() + " " + catalog
                + " exactly once. It named " + given.size() + ".");
        }
    }

    private Priority requirePriority(String priorityId) {
        return priorityRepository.findById(priorityId)
            .orElseThrow(() -> new ResourceNotFoundException("Priority not found: " + priorityId));
    }

    private Resolution requireResolution(String resolutionId) {
        return resolutionRepository.findById(resolutionId)
            .orElseThrow(() -> new ResourceNotFoundException("Resolution not found: " + resolutionId));
    }

    private LinkType requireLinkType(String linkTypeId) {
        return linkTypeRepository.findById(linkTypeId)
            .orElseThrow(() -> new ResourceNotFoundException("Link type not found: " + linkTypeId));
    }

    private PriorityResponse toResponse(Priority priority) {
        return new PriorityResponse(priority.getId(), priority.getName(), priority.getSequence(), priority.getColor());
    }

    private ResolutionResponse toResponse(Resolution resolution) {
        return new ResolutionResponse(resolution.getId(), resolution.getName(), resolution.getDescription());
    }

}
