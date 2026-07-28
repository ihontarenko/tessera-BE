package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Component;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.component.ComponentResponse;
import net.innoventa.tessera.dto.component.SaveComponentRequest;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.ComponentRepository;
import net.innoventa.tessera.repository.IssueComponentRepository;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

/**
 * Project components (ticket 06): named areas of a project with an optional lead. Listing needs only
 * membership; creating, editing and deleting require {@code ADMINISTER_PROJECT}. A component's name is
 * unique within its project. Deleting one also detaches it from any issues that referenced it.
 */
@Service
@RequiredArgsConstructor
public class ComponentService {

    private final ComponentRepository componentRepository;
    private final IssueComponentRepository issueComponentRepository;
    private final MemberRepository memberRepository;
    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final Supplier<String> idGenerator;

    @Transactional(readOnly = true)
    public List<ComponentResponse> list(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.requireVisible(caller.getId(), projectId);

        return componentRepository.findByProjectIdOrderByNameAsc(projectId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ComponentResponse create(Jwt jwt, String projectId, SaveComponentRequest request) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.require(caller, projectId, Permissions.ADMINISTER_PROJECT);

        if (componentRepository.existsByProjectIdAndName(projectId, request.name())) {
            throw new BusinessRuleViolationException("A component named '" + request.name() + "' already exists");
        }

        Component component = componentRepository.save(Component.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .name(request.name())
            .leadMemberId(resolveLead(request.leadMemberId()))
            .description(request.description())
            .build());

        return toResponse(component);
    }

    @Transactional
    public ComponentResponse update(Jwt jwt, String componentId, SaveComponentRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Component component = requireComponent(componentId);
        projectPermissionService.require(caller, component.getProjectId(), Permissions.ADMINISTER_PROJECT);

        if (componentRepository.existsByProjectIdAndNameAndIdNot(component.getProjectId(), request.name(), componentId)) {
            throw new BusinessRuleViolationException("A component named '" + request.name() + "' already exists");
        }

        component.setName(request.name());
        component.setLeadMemberId(resolveLead(request.leadMemberId()));
        component.setDescription(request.description());

        return toResponse(component);
    }

    @Transactional
    public void delete(Jwt jwt, String componentId) {
        Member caller = memberService.resolveMember(jwt);
        Component component = requireComponent(componentId);
        projectPermissionService.require(caller, component.getProjectId(), Permissions.ADMINISTER_PROJECT);

        issueComponentRepository.deleteAll(issueComponentRepository.findByComponentId(componentId));
        componentRepository.delete(component);
    }

    private String resolveLead(String leadMemberId) {
        return leadMemberId == null ? null : memberService.requireMember(leadMemberId).getId();
    }

    private ComponentResponse toResponse(Component component) {
        MemberSummary lead = component.getLeadMemberId() == null
            ? null
            : memberRepository.findById(component.getLeadMemberId()).map(MemberSummary::from).orElse(null);

        return new ComponentResponse(
            component.getId(),
            component.getProjectId(),
            component.getName(),
            lead,
            component.getDescription()
        );
    }

    private Component requireComponent(String componentId) {
        return componentRepository.findById(componentId)
            .orElseThrow(() -> new ResourceNotFoundException("Component not found: " + componentId));
    }

}
