package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.SavedFilter;
import net.innoventa.tessera.domain.SavedFilterVisibility;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.filter.SaveFilterRequest;
import net.innoventa.tessera.dto.filter.SavedFilterView;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ForbiddenException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.SavedFilterRepository;
import net.innoventa.tessera.service.filter.BoardFilterEvaluator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Saved board filters (ADR-0008): a member names a jME predicate once and reuses it, or shares it
 * with the project.
 * <p>
 * Reads go through the same {@code requireVisible} gate every project-scoped read uses (non-member
 * {@code 404}, member without {@code BROWSE_PROJECT} {@code 403}), and a member sees the project's
 * shared filters plus their own private ones. Writes need no extra permission — saving a filter
 * creates no access, it only names a question — but only the <strong>owner</strong> may change or
 * delete one, whatever its visibility. Sharing a filter is not surrendering it.
 * <p>
 * Every write validates the expression through {@link BoardFilterEvaluator}, so a stored filter
 * always parses. A filter cannot widen anyone's view either: it is applied over the board slice the
 * caller could already read, never against the author's.
 * <p>
 * The list also carries the shipped presets (migration {@code V000010}) — {@code GLOBAL} rows with no
 * project and no owner. They read like any other filter and refuse every write, which is the whole
 * distinction: a preset is something a member <em>uses</em>, never something they administer.
 */
@Service
@RequiredArgsConstructor
public class SavedFilterService {

    private final SavedFilterRepository savedFilterRepository;
    private final MemberRepository memberRepository;
    private final ProjectService projectService;
    private final MemberService memberService;
    private final BoardFilterEvaluator boardFilterEvaluator;
    private final Supplier<String> idGenerator;

    @Transactional(readOnly = true)
    public List<SavedFilterView> list(Jwt jwt, String projectId) {
        Member caller = requireVisibleProject(jwt, projectId);

        List<SavedFilter> savedFilters = savedFilterRepository.findVisibleTo(
            projectId, caller.getId(), SavedFilterVisibility.PROJECT, SavedFilterVisibility.GLOBAL);

        Map<String, Member> owners = ownersOf(savedFilters);

        return savedFilters.stream()
            .map(savedFilter -> view(savedFilter, owners.get(savedFilter.getOwnerMemberId()), caller))
            .toList();
    }

    @Transactional
    public SavedFilterView create(Jwt jwt, String projectId, SaveFilterRequest request) {
        Member caller = requireVisibleProject(jwt, projectId);
        requireOwnableVisibility(request.visibility());
        boardFilterEvaluator.requireStorable(request.expression());
        requireUnusedName(projectId, caller.getId(), request.name());

        SavedFilter savedFilter = savedFilterRepository.save(SavedFilter.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .ownerMemberId(caller.getId())
            .name(request.name())
            .description(request.description())
            .expression(request.expression())
            .visibility(request.visibility())
            .build());

        return view(savedFilter, caller, caller);
    }

    @Transactional
    public SavedFilterView update(Jwt jwt, String projectId, String savedFilterId, SaveFilterRequest request) {
        Member caller = requireVisibleProject(jwt, projectId);
        requireOwnableVisibility(request.visibility());
        SavedFilter savedFilter = requireOwnedFilter(projectId, savedFilterId, caller);
        boardFilterEvaluator.requireStorable(request.expression());

        if (!savedFilter.getName().equals(request.name())) {
            requireUnusedName(projectId, caller.getId(), request.name());
        }

        savedFilter.setName(request.name());
        savedFilter.setDescription(request.description());
        savedFilter.setExpression(request.expression());
        savedFilter.setVisibility(request.visibility());

        return view(savedFilter, caller, caller);
    }

    @Transactional
    public void delete(Jwt jwt, String projectId, String savedFilterId) {
        Member caller = requireVisibleProject(jwt, projectId);
        savedFilterRepository.delete(requireOwnedFilter(projectId, savedFilterId, caller));
    }

    /**
     * The acting member, for a route that has already been refused about the project.
     *
     * <p>{@code SavedFilterController} declares {@code BROWSE_PROJECT} at the project for every route
     * here, so the visibility gate this used to run is the route's. What remains is resolving the caller
     * and asserting the project exists.
     */
    private Member requireVisibleProject(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);

        return caller;
    }

    /**
     * A filter the caller owns, or a refusal. A filter belonging to someone else is a {@code 403}
     * rather than a {@code 404} — the caller can see it in their list, so pretending it does not exist
     * would only be confusing. A shipped preset is the same {@code 403} with its own wording: it is not
     * that the caller lost a race for ownership, it is that the row has no owner to be.
     */
    private SavedFilter requireOwnedFilter(String projectId, String savedFilterId, Member caller) {
        // Looked up by id alone, not by (id, project): a preset carries no project, and resolving it to
        // a 404 here would answer "no such filter" about a row the caller is looking straight at.
        SavedFilter savedFilter = savedFilterRepository.findById(savedFilterId)
            .filter(candidate -> candidate.getVisibility() == SavedFilterVisibility.GLOBAL
                || projectId.equals(candidate.getProjectId()))
            .orElseThrow(() -> new ResourceNotFoundException("Saved filter not found: " + savedFilterId));

        if (savedFilter.getVisibility() == SavedFilterVisibility.GLOBAL) {
            throw new ForbiddenException(
                "'" + savedFilter.getName() + "' is a built-in preset — copy it to a filter of your own to change it");
        }

        if (!savedFilter.getOwnerMemberId().equals(caller.getId())) {
            throw new ForbiddenException("Only the member who saved '" + savedFilter.getName() + "' can change it");
        }

        return savedFilter;
    }

    /**
     * A member may save a filter for themselves or share it with the project; they may not mint a
     * preset. {@code GLOBAL} is seeded by migration and read-only over the API — without this check the
     * visibility field would be a way to create a row nobody, including its author, could ever delete.
     */
    private void requireOwnableVisibility(SavedFilterVisibility visibility) {
        if (visibility == SavedFilterVisibility.GLOBAL) {
            throw new BusinessRuleViolationException("A filter can be private or shared with the project, not global");
        }
    }

    private void requireUnusedName(String projectId, String ownerMemberId, String name) {
        if (savedFilterRepository.existsByProjectIdAndOwnerMemberIdAndName(projectId, ownerMemberId, name)) {
            throw new BusinessRuleViolationException("You already have a filter named '" + name + "' in this project");
        }
    }

    private Map<String, Member> ownersOf(List<SavedFilter> savedFilters) {
        List<String> ownerIds = savedFilters.stream()
            .map(SavedFilter::getOwnerMemberId)
            .filter(ownerMemberId -> ownerMemberId != null)
            .distinct()
            .toList();

        return memberRepository.findAllById(ownerIds).stream()
            .collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    /** A preset has no owner, so it is editable by nobody — including the member reading it. */
    private SavedFilterView view(SavedFilter savedFilter, Member owner, Member caller) {
        return SavedFilterView.from(
            savedFilter,
            owner == null ? null : MemberSummary.from(owner),
            caller.getId().equals(savedFilter.getOwnerMemberId())
        );
    }

}
