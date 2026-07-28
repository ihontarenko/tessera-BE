package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Version;
import net.innoventa.tessera.dto.version.SaveVersionRequest;
import net.innoventa.tessera.dto.version.VersionResponse;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.IssueVersionRepository;
import net.innoventa.tessera.repository.VersionRepository;
import net.innoventa.tessera.security.Permissions;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

/**
 * Project versions (ticket 06): release markers carrying a {@link net.innoventa.tessera.domain.VersionState}
 * lifecycle (never released/archived booleans). Listing needs only membership; creating, editing and
 * deleting require {@code ADMINISTER_PROJECT}. A version's name is unique within its project. New
 * versions are appended after the current highest {@code sequence}; deleting one detaches it from any
 * issues that referenced it.
 */
@Service
@RequiredArgsConstructor
public class VersionService {

    private final VersionRepository versionRepository;
    private final IssueVersionRepository issueVersionRepository;
    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final MemberService memberService;
    private final Supplier<String> idGenerator;

    @Transactional(readOnly = true)
    public List<VersionResponse> list(Jwt jwt, String projectId) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.requireVisible(caller.getId(), projectId);

        return versionRepository.findByProjectIdOrderBySequenceAscNameAsc(projectId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public VersionResponse create(Jwt jwt, String projectId, SaveVersionRequest request) {
        Member caller = memberService.resolveMember(jwt);
        projectService.requireProject(projectId);
        projectPermissionService.require(caller, projectId, Permissions.ADMINISTER_PROJECT);

        if (versionRepository.existsByProjectIdAndName(projectId, request.name())) {
            throw new BusinessRuleViolationException("A version named '" + request.name() + "' already exists");
        }

        Version version = versionRepository.save(Version.builder()
            .id(idGenerator.get())
            .projectId(projectId)
            .name(request.name())
            .description(request.description())
            .releaseDate(request.releaseDate())
            .state(request.state())
            .sequence(nextSequence(projectId))
            .build());

        return toResponse(version);
    }

    @Transactional
    public VersionResponse update(Jwt jwt, String versionId, SaveVersionRequest request) {
        Member caller = memberService.resolveMember(jwt);
        Version version = requireVersion(versionId);
        projectPermissionService.require(caller, version.getProjectId(), Permissions.ADMINISTER_PROJECT);

        if (versionRepository.existsByProjectIdAndNameAndIdNot(version.getProjectId(), request.name(), versionId)) {
            throw new BusinessRuleViolationException("A version named '" + request.name() + "' already exists");
        }

        version.setName(request.name());
        version.setDescription(request.description());
        version.setReleaseDate(request.releaseDate());
        version.setState(request.state());

        return toResponse(version);
    }

    @Transactional
    public void delete(Jwt jwt, String versionId) {
        Member caller = memberService.resolveMember(jwt);
        Version version = requireVersion(versionId);
        projectPermissionService.require(caller, version.getProjectId(), Permissions.ADMINISTER_PROJECT);

        issueVersionRepository.deleteAll(issueVersionRepository.findByVersionId(versionId));
        versionRepository.delete(version);
    }

    private int nextSequence(String projectId) {
        return versionRepository.findByProjectIdOrderBySequenceAscNameAsc(projectId).stream()
            .mapToInt(Version::getSequence)
            .max()
            .orElse(0) + 1;
    }

    private VersionResponse toResponse(Version version) {
        return new VersionResponse(
            version.getId(),
            version.getProjectId(),
            version.getName(),
            version.getDescription(),
            version.getReleaseDate(),
            version.getState(),
            version.getSequence()
        );
    }

    private Version requireVersion(String versionId) {
        return versionRepository.findById(versionId)
            .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionId));
    }

}
