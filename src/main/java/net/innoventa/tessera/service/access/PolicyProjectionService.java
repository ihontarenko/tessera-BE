package net.innoventa.tessera.service.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.repository.ProjectRepository;
import org.jmouse.access.PermissionCatalog;
import org.jmouse.access.ScopeCatalog;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.jpa.AccessDisclosure;
import org.jmouse.access.policy.model.PolicyDocument;
import org.jmouse.access.policy.model.PolicyPermissionDeclaration;
import org.jmouse.access.projection.PolicyProjection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The access screen's fourth tab: <strong>what is actually in force</strong>, as a {@code .jmp} document
 * (TSSR-20).
 *
 * <p>Everything about <em>why</em> is in {@link PolicyProjection}, which is the library's — this product
 * is one of its callers and deliberately not another copy (JMF-17). This class is the thin half: it
 * fetches the rows and answers the two questions the projection cannot — which scopes to declare, and
 * what to call a scope instance.
 *
 * <h2>⚠️ The scope block is rendered from {@code all()}, never {@code floors()}</h2>
 *
 * <p>A catalogue's floors are the scopes a grant may name an <em>instance</em> of, and in Tessera that
 * is {@code PROJECT} alone — {@code GLOBAL} is the widest scope and {@code SELF} is own-rows, and
 * neither is a place. Rendering the block from {@code floors()} would emit
 * {@code declare scopes { @PROJECT }} above roles whose every entry reads {@code @GLOBAL}: a document
 * that declares a vocabulary the engine does not have, and that would not parse back. The same trap is
 * documented on {@code AccessAdministrationService}, which fell into it first.
 *
 * <h2>⚠️ A project is named by its key, not by its identifier</h2>
 *
 * <p>{@code grants PROJECT_ADMINISTRATOR @PROJECT:8f21-…} is true and unreadable, and this screen exists
 * to be read. Tessera's scope is flat — a holding at a project means that project and nothing under it —
 * so unlike WiQi, where the name has to carry the reach of a subtree, here the key is the whole answer.
 */
@Service
@RequiredArgsConstructor
public class PolicyProjectionService {

    private final AccessAdministration access;
    private final AccessDisclosure     disclosure;
    private final ScopeCatalog         scopes;
    private final PolicyDocument       document;
    private final ProjectRepository    projects;

    /** ⚠️ The vocabulary as the policy documents declare it — both axes, so the projection is whole. */
    private final PermissionCatalog    vocabulary;

    @Value("${jmouse.access.policy.name:tessera}")
    private String policyName;

    /**
     * ⚠️ One read of the projects, and every key resolved from it. Asking per holding would be a query
     * per row on the one screen that lists every grant in the installation.
     */
    @Transactional(readOnly = true)
    public String render() {
        Map<String, String> described = document.permissions().stream()
                .collect(Collectors.toMap(
                        PolicyPermissionDeclaration::name,
                        declaration -> declaration.description() == null ? "" : declaration.description(),
                        (first, second) -> first));

        Map<String, String> keys = projects.findAll().stream()
                .collect(Collectors.toMap(Project::getId, Project::getKey));

        return PolicyProjection.of(policyName)
                // ⚠️ The catalogue, so the projection shows BOTH axes — what a subject may do, and which
                // actions may be reached through a tool. This screen exists to answer "what is actually
                // in force here" in one piece; leaving a whole axis out of it would make the one place
                // that claims to be complete the one that quietly is not.
                .permissions(List.copyOf(vocabulary.all()), described::get)
                .scopes(scopes.all())
                .roles(access.roles())
                .holdings(disclosure.roleHoldings(), disclosure.directHoldings())
                .naming(keys::get)
                .render();
    }
}
