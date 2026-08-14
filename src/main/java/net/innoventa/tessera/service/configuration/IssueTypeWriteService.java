package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.IssueType;
import net.innoventa.tessera.dto.configuration.IssueTypeLevelImpact;
import net.innoventa.tessera.dto.configuration.IssueTypeLevelImpact.HierarchyPair;
import net.innoventa.tessera.dto.configuration.IssueTypeRequest;
import net.innoventa.tessera.dto.configuration.IssueTypeResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueRepository;
import net.innoventa.tessera.repository.IssueTypePairCount;
import net.innoventa.tessera.repository.IssueTypeRepository;
import net.innoventa.tessera.repository.SprintIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Writing the issue-type catalog, and saying first what a level change would invalidate.
 *
 * <p>{@code hierarchyLevel} is the interesting field, in the same way a status's category is. It is not
 * a label: level 1 contains other work, level 0 is what a board shows and a sprint plans (ADR-0014), and
 * level −1 always belongs to a parent. Moving a type between them changes what may be a parent of what
 * and what may be committed to a sprint — for issues that already exist and are already arranged.
 *
 * <p>⚠️ <strong>Allowed, reported, and never repaired.</strong> Existing hierarchies are left exactly as
 * they are. The report says how many pairings would stop satisfying "a parent sits strictly higher" and
 * how many issues would be sitting in sprints they can no longer be planned into; which end of each pair
 * was wrong is a judgement about somebody's work, and the parent control is where it gets made.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IssueTypeWriteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IssueTypeWriteService.class);

    /** The level a board renders and a sprint plans (ADR-0014) — the one leaving it is worth reporting. */
    private static final int PLANNED_LEVEL = 0;

    private final IssueTypeRepository   issueTypeRepository;
    private final IssueRepository       issueRepository;
    private final SprintIssueRepository sprintIssueRepository;
    private final ConfigurationUsage    configurationUsage;
    private final Supplier<String>      idGenerator;

    public IssueTypeResponse create(IssueTypeRequest request) {
        String name = CatalogRules.requireName(request.name(), "issue type");
        CatalogRules.requireNameAvailable(issueTypeRepository.existsByNameIgnoreCase(name), "issue type", name);
        requireDrawableIcon(request.iconKey());

        IssueType issueType = issueTypeRepository.save(IssueType.builder()
            .id(idGenerator.get())
            .name(name)
            .hierarchyLevel(request.hierarchyLevel())
            .iconKey(request.iconKey())
            .description(request.description())
            .build());

        LOGGER.info("Issue type '{}' created at hierarchy level {} — no scheme grants it until one is "
                    + "edited, so no project can raise one yet", name, request.hierarchyLevel());

        return toResponse(issueType);
    }

    public IssueTypeResponse update(String issueTypeId, IssueTypeRequest request) {
        IssueType issueType = requireIssueType(issueTypeId);
        String name = CatalogRules.requireName(request.name(), "issue type");
        CatalogRules.requireNameAvailable(
            issueTypeRepository.existsByNameIgnoreCaseAndIdNot(name, issueTypeId), "issue type", name);
        requireDrawableIcon(request.iconKey());

        if (issueType.getHierarchyLevel() != request.hierarchyLevel()) {
            LOGGER.info("Issue type '{}' moves from hierarchy level {} to {} — existing hierarchies are "
                        + "left as they are", issueType.getName(), issueType.getHierarchyLevel(),
                request.hierarchyLevel());
        }

        if (!issueType.getName().equals(name)) {
            LOGGER.info("Issue type '{}' renamed to '{}'", issueType.getName(), name);
        }

        issueType.setName(name);
        issueType.setHierarchyLevel(request.hierarchyLevel());
        issueType.setIconKey(request.iconKey());
        issueType.setDescription(request.description());

        return toResponse(issueType);
    }

    public void delete(String issueTypeId) {
        IssueType issueType = requireIssueType(issueTypeId);

        CatalogRules.requireCatalogSurvives(issueTypeRepository.count() - 1, "issue type",
            "every issue is one, so an empty catalog is a tracker nothing can be raised in.");
        CatalogRules.requireNothingHoldsIt(
            configurationUsage.ofIssueType(issueTypeId), "issue type", issueType.getName());

        issueTypeRepository.delete(issueType);

        LOGGER.info("Issue type '{}' deleted", issueType.getName());
    }

    /**
     * Which existing hierarchies a proposed level would invalidate, and how much sprint work it would
     * unplan.
     *
     * <p>Computed from the pairings and their sizes rather than by walking the issues: "a parent sits
     * strictly higher" is a rule about two levels, so the whole answer is the set of (parent type, child
     * type) combinations that exist — one grouped query, whatever the table's size.
     */
    @Transactional(readOnly = true)
    public IssueTypeLevelImpact levelImpact(String issueTypeId, int proposedLevel) {
        IssueType issueType = requireIssueType(issueTypeId);

        Map<String, IssueType> byId = issueTypeRepository.findAll().stream()
            .collect(Collectors.toMap(IssueType::getId, candidate -> candidate));

        List<HierarchyPair> violations = issueRepository.countParentChildTypePairs().stream()
            .filter(pair -> namesThisType(pair, issueTypeId))
            .map(pair -> asHierarchyPair(pair, byId, issueTypeId, proposedLevel))
            .filter(pair -> pair != null && wouldBreak(pair, byId))
            .toList();

        boolean leavingPlannedLevel =
            issueType.getHierarchyLevel() == PLANNED_LEVEL && proposedLevel != PLANNED_LEVEL;

        return new IssueTypeLevelImpact(
            issueType.getId(),
            issueType.getName(),
            issueType.getHierarchyLevel(),
            proposedLevel,
            violations,
            leavingPlannedLevel ? sprintIssueRepository.countCommitmentsOfIssueType(issueTypeId) : 0L);
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * ⚠️ Only pairings the changed type is actually <em>in</em>.
     *
     * <p>Without this the report described the whole table: an Epic-under-a-Story hierarchy that was
     * already inconsistent before anybody touched anything would be listed under "this would invalidate
     * existing work", which is both untrue and the fastest way to teach somebody to ignore the warning.
     */
    private static boolean namesThisType(IssueTypePairCount pair, String issueTypeId) {
        return issueTypeId.equals(pair.parentIssueTypeId()) || issueTypeId.equals(pair.childIssueTypeId());
    }

    /**
     * ⚠️ Broken <em>by the change</em> — not merely broken.
     *
     * <p>A pairing that already violates "a parent sits strictly higher" keeps violating it whatever
     * happens next, and reporting it as a consequence would be blaming this edit for somebody else's.
     * So the rule is applied twice, to the levels as stored and to the levels as proposed, and only a
     * pairing that passes now and fails after is a finding.
     */
    private static boolean wouldBreak(HierarchyPair proposed, Map<String, IssueType> byId) {
        IssueType parent = byId.get(proposed.parentIssueTypeId());
        IssueType child = byId.get(proposed.childIssueTypeId());

        if (parent == null || child == null) {
            return false;
        }

        boolean legalToday = parent.getHierarchyLevel() > child.getHierarchyLevel();
        boolean legalAfter = proposed.parentLevel() > proposed.childLevel();

        return legalToday && !legalAfter;
    }

    /**
     * One pairing read with the proposed level substituted for the type being changed, or null where
     * either end names a type that no longer exists.
     *
     * <p>The substitution is what makes this a prediction rather than a description: every other type
     * keeps its stored level, and only the one under discussion is read as it would be.
     */
    private static HierarchyPair asHierarchyPair(
        IssueTypePairCount pair, Map<String, IssueType> byId, String changingId, int proposedLevel) {

        IssueType parent = byId.get(pair.parentIssueTypeId());
        IssueType child = byId.get(pair.childIssueTypeId());

        if (parent == null || child == null) {
            return null;
        }

        return new HierarchyPair(
            parent.getId(),
            parent.getName(),
            levelAfter(parent, changingId, proposedLevel),
            child.getId(),
            child.getName(),
            levelAfter(child, changingId, proposedLevel),
            pair.count());
    }

    private static int levelAfter(IssueType issueType, String changingId, int proposedLevel) {
        return issueType.getId().equals(changingId) ? proposedLevel : issueType.getHierarchyLevel();
    }

    /**
     * ⚠️ Refused rather than accepted-and-rendered-generically. The fallback icon is why: an unknown key
     * produces a type that looks exactly like a correct one, forever, with nothing anywhere reporting it.
     */
    private static void requireDrawableIcon(String iconKey) {
        if (IssueTypeIcons.isAcceptable(iconKey)) {
            return;
        }

        throw new BusinessRuleViolationException(
            "'" + iconKey + "' is not an icon this build draws. Choose one of: "
            + String.join(", ", IssueTypeIcons.ALL) + ".");
    }

    private IssueType requireIssueType(String issueTypeId) {
        return issueTypeRepository.findById(issueTypeId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue type not found: " + issueTypeId));
    }

    private static IssueTypeResponse toResponse(IssueType issueType) {
        return new IssueTypeResponse(
            issueType.getId(),
            issueType.getName(),
            issueType.getHierarchyLevel(),
            issueType.getIconKey(),
            issueType.getDescription());
    }

}
