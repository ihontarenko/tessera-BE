package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.domain.IssueTypeScheme;
import net.innoventa.tessera.domain.IssueTypeSchemeItem;
import net.innoventa.tessera.dto.configuration.IssueTypeSchemeRequest;
import net.innoventa.tessera.dto.configuration.IssueTypeSchemeResponse;
import net.innoventa.tessera.dto.configuration.SchemeMemberImpact;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeItemRepository;
import net.innoventa.tessera.repository.IssueTypeSchemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

/**
 * Writing issue-type schemes — which types a project may raise, in what order, and which is preselected.
 *
 * <p>⚠️ <strong>A scheme is written whole.</strong> Membership, order and default arrive in one payload
 * and the item rows are replaced rather than diffed, because the only rule that matters here — the
 * default is one of the members — is a statement about all three at once. See
 * {@link SchemeRules#requireDefaultIsMember}.
 *
 * <p>⚠️ <strong>Narrowing a scheme is allowed, and it is reported rather than refused.</strong> Removing
 * Bug from a scheme leaves every existing bug exactly where it is, readable and editable and still a
 * Bug; what stops is raising new ones in the projects on that scheme. Refusing the removal would make a
 * scheme impossible to narrow the moment anybody used it, which is the opposite of editable. The count
 * is offered first through {@link #removalImpact} so the decision is made knowing the number.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IssueTypeSchemeWriteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IssueTypeSchemeWriteService.class);

    private static final String KIND = "issue-type scheme";

    private final IssueTypeSchemeRepository     issueTypeSchemeRepository;
    private final IssueTypeSchemeItemRepository issueTypeSchemeItemRepository;
    private final IssueTypeRepository           issueTypeRepository;
    private final ConfigurationUsage            configurationUsage;
    private final Supplier<String>              idGenerator;

    public IssueTypeSchemeResponse create(IssueTypeSchemeRequest request) {
        String name = SchemeRules.requireName(request.name(), KIND);
        SchemeRules.requireNameAvailable(
            issueTypeSchemeRepository.existsByNameIgnoreCase(name), KIND, name);

        List<String> issueTypeIds = requireIssueTypes(request);

        IssueTypeScheme scheme = issueTypeSchemeRepository.save(IssueTypeScheme.builder()
            .id(idGenerator.get())
            .name(name)
            .defaultIssueTypeId(request.defaultIssueTypeId())
            .description(request.description())
            .build());

        replaceItems(scheme.getId(), issueTypeIds);

        LOGGER.info("Issue-type scheme '{}' created with {} types — no project is on it until one is "
                    + "pointed at it", name, issueTypeIds.size());

        return toResponse(scheme, issueTypeIds);
    }

    public IssueTypeSchemeResponse update(String schemeId, IssueTypeSchemeRequest request) {
        IssueTypeScheme scheme = requireScheme(schemeId);
        String name = SchemeRules.requireName(request.name(), KIND);
        SchemeRules.requireNameAvailable(
            issueTypeSchemeRepository.existsByNameIgnoreCaseAndIdNot(name, schemeId), KIND, name);

        List<String> issueTypeIds = requireIssueTypes(request);
        List<String> removed = membersOf(schemeId).stream()
            .filter(issueTypeId -> !issueTypeIds.contains(issueTypeId))
            .toList();

        scheme.setName(name);
        scheme.setDefaultIssueTypeId(request.defaultIssueTypeId());
        scheme.setDescription(request.description());
        replaceItems(schemeId, issueTypeIds);

        if (!removed.isEmpty()) {
            LOGGER.info("Issue-type scheme '{}' no longer grants {} — existing issues of those types keep "
                        + "them, and no new ones can be raised on this scheme", name, names(removed));
        }

        return toResponse(scheme, issueTypeIds);
    }

    public void delete(String schemeId) {
        IssueTypeScheme scheme = requireScheme(schemeId);

        SchemeRules.requireKindSurvives(issueTypeSchemeRepository.count() - 1, KIND);
        CatalogRules.requireNothingHoldsIt(
            configurationUsage.ofIssueTypeScheme(schemeId), KIND, scheme.getName());

        issueTypeSchemeItemRepository.deleteAll(
            issueTypeSchemeItemRepository.findBySchemeIdOrderBySequenceAsc(schemeId));
        issueTypeSchemeRepository.delete(scheme);

        LOGGER.info("Issue-type scheme '{}' deleted", scheme.getName());
    }

    /**
     * What removing a type from this scheme would mean — how much work of that type the projects on the
     * scheme already hold.
     *
     * <p>Asked before the edit, answered as a number rather than as a verdict: those issues are not
     * touched, and whether the number is a reason to stop is the administrator's call.
     */
    @Transactional(readOnly = true)
    public SchemeMemberImpact removalImpact(String schemeId, String issueTypeId) {
        requireScheme(schemeId);
        IssueType issueType = requireIssueType(issueTypeId);

        return new SchemeMemberImpact(
            issueType.getId(),
            issueType.getName(),
            configurationUsage.issuesOfTypeOnIssueTypeScheme(schemeId, issueTypeId),
            configurationUsage.projectsOnIssueTypeScheme(schemeId).size());
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * The requested membership, checked to be real types, distinct, and to contain the default.
     *
     * <p>⚠️ Every identifier is resolved rather than trusted. A scheme granting a type that does not
     * exist is a picker with a blank row in it, and the row that produced it is long gone by the time
     * anybody notices.
     */
    private List<String> requireIssueTypes(IssueTypeSchemeRequest request) {
        List<String> issueTypeIds = request.issueTypeIds();

        SchemeRules.requireNoDuplicates(issueTypeIds, "issue type");
        SchemeRules.requireDefaultIsMember(issueTypeIds, request.defaultIssueTypeId(), "issue type");

        issueTypeIds.forEach(this::requireIssueType);

        return issueTypeIds;
    }

    /**
     * ⚠️ Replaced, not diffed. The item rows carry nothing but membership and position, so a delete and
     * a re-insert produce exactly the same table as a minimal patch would — and it is one rule
     * ("position is the order") rather than three about what moved where.
     */
    private void replaceItems(String schemeId, List<String> issueTypeIds) {
        issueTypeSchemeItemRepository.deleteAll(
            issueTypeSchemeItemRepository.findBySchemeIdOrderBySequenceAsc(schemeId));
        issueTypeSchemeItemRepository.flush();

        for (int sequence = 0; sequence < issueTypeIds.size(); sequence++) {
            issueTypeSchemeItemRepository.save(IssueTypeSchemeItem.builder()
                .id(idGenerator.get())
                .schemeId(schemeId)
                .issueTypeId(issueTypeIds.get(sequence))
                .sequence(sequence)
                .build());
        }
    }

    private List<String> membersOf(String schemeId) {
        return issueTypeSchemeItemRepository.findBySchemeIdOrderBySequenceAsc(schemeId).stream()
            .map(IssueTypeSchemeItem::getIssueTypeId)
            .toList();
    }

    private String names(List<String> issueTypeIds) {
        return String.join(", ", issueTypeRepository.findAllById(issueTypeIds).stream()
            .map(IssueType::getName)
            .sorted()
            .toList());
    }

    private IssueTypeScheme requireScheme(String schemeId) {
        return issueTypeSchemeRepository.findById(schemeId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue type scheme not found: " + schemeId));
    }

    private IssueType requireIssueType(String issueTypeId) {
        return issueTypeRepository.findById(issueTypeId)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "This scheme names an issue type that does not exist: " + issueTypeId));
    }

    private static IssueTypeSchemeResponse toResponse(IssueTypeScheme scheme, List<String> issueTypeIds) {
        return new IssueTypeSchemeResponse(
            scheme.getId(),
            scheme.getName(),
            scheme.getDescription(),
            scheme.getDefaultIssueTypeId(),
            List.copyOf(issueTypeIds));
    }

}
