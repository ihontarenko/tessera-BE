package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.Project;
import net.innoventa.tessera.dto.MemberSummary;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.AccessOverview;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.BundleEntryView;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.DirectHoldingView;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.PermissionView;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.ProjectRef;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.RoleHoldingView;
import net.innoventa.tessera.dto.access.AccessAdministrationDtos.RoleView;
import net.innoventa.tessera.exception.BusinessRuleViolationException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.repository.ProjectRepository;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Targets;
import net.innoventa.tessera.security.access.TesseraScope;
import org.jmouse.access.ScopeCatalog;
import org.jmouse.access.ScopeKind;
import org.jmouse.access.ScopeReference;
import org.jmouse.access.jpa.AccessAdministration;
import org.jmouse.access.jpa.AccessDisclosure;
import org.jmouse.access.policy.model.PolicyDocument;
import org.jmouse.access.policy.model.PolicyPermissionDeclaration;
import org.jmouse.access.policy.model.PolicyRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The screen behind {@link Permissions#ADMINISTER_ACCESS} — what the roles carry, and who holds them.
 *
 * <p><strong>It reads rows and writes rows.</strong> {@code policy/tessera.jmp} is what a fresh
 * installation is born with; from the first start onwards the tables are the only thing the engine
 * consults, so a bundle edited here changes authorization with no deploy. That is the point of the
 * screen and the reason it is behind a permission of its own.
 *
 * <h2>⚠️ Two things this deliberately does not do</h2>
 *
 * <ul>
 *   <li><strong>It does not create or delete roles.</strong> A role's name appears in {@code Roles} and
 *       in the seed, and one invented at runtime would be a name no code can hand out — a row that
 *       exists and grants nobody anything. Adding one is a document change and a constant.
 *   <li><strong>It does not move {@code assignableAt}.</strong> That is what stops
 *       {@code PROJECT_ADMINISTRATOR} being granted installation-wide, and a field that guards against a
 *       mistake must not be editable by whoever is making it.
 * </ul>
 *
 * <h2>⚠️ What a re-seed does to an edit</h2>
 *
 * <p>{@code PolicySeedStep} rewrites the bundle of every role the document declares whenever the
 * document's checksum moves. So an edit here to a declared role survives until somebody changes the
 * file, and then does not. The screen has to say so — {@link RoleView#declared()} is what it says it
 * with — because the alternative is an administrator discovering it after a deploy.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessAdministrationService {

    private final AccessAdministration access;
    private final AccessDisclosure     disclosure;
    private final ScopeCatalog         scopes;
    private final PolicyDocument       document;
    private final MemberRepository     members;
    private final ProjectRepository    projects;

    /**
     * Everything the screen shows, in one request.
     *
     * <p>⚠️ <strong>It walks every holding in the installation</strong>, which is what
     * {@link AccessDisclosure} is for and why it is a separate port the engine may not hold. It is a
     * disclosure surface: reading it is knowing who can do what to whom, which is exactly the thing a
     * permission of its own and an audit trail exist to govern.
     */
    public AccessOverview overview() {
        Map<String, MemberSummary> whoIsWho = members.findAll().stream()
                .collect(Collectors.toMap(Member::getId, MemberSummary::from));

        Map<String, ProjectRef> whereIsWhere = projects.findAll().stream()
                .collect(Collectors.toMap(Project::getId, AccessAdministrationService::referenceOf));

        return new AccessOverview(
                permissions(),
                roles(),
                roleHoldings(whoIsWho, whereIsWhere),
                directHoldings(whoIsWho, whereIsWhere));
    }

    /**
     * What a role carries from now on.
     *
     * <p>⚠️ <strong>The whole bundle, never a difference.</strong> A screen that sent "add this one"
     * would have no way to express a removal that raced with another administrator's addition; sending
     * the set makes the last save win, visibly, which is the behaviour anybody editing a list expects.
     *
     * @throws ResourceNotFoundException      where no role goes by that name
     * @throws BusinessRuleViolationException where a line names a permission or a scope this build does
     *                                        not register — a bundle entry nothing can match is a grant
     *                                        that silently confers nothing, which is the failure the
     *                                        whole model exists to prevent
     */
    @Transactional
    public RoleView setBundle(String roleName, List<BundleEntryView> bundle) {
        AccessAdministration.RoleView existing = access.roles().stream()
                .filter(role -> role.name().equals(roleName))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));

        bundle.forEach(this::requireItCanMatchSomething);

        access.setBundle(roleName, bundle.stream()
                .map(entry -> new AccessAdministration.BundleEntry(entry.permission(), entry.carriedAt()))
                .toList());

        return viewOf(existing.name(), existing.assignableAt(), bundle);
    }

    // ── Who holds what ────────────────────────────────────────────────────────

    /**
     * Give somebody a role, at the installation or at one project.
     *
     * <p>⚠️ <strong>Where it may be handed out is the role's own answer, not the caller's.</strong>
     * {@code assignableAt} comes from the policy document for exactly this reason: a screen that let
     * {@code PROJECT_ADMINISTRATOR} be granted at {@code @GLOBAL} would make one click equivalent to
     * administering every project there will ever be — including the ones nobody has made yet.
     */
    @Transactional
    public void assign(String memberId, String roleName, String projectId, String by) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        access.assign(member.getId(), roleName, placeFor(roleName, projectId), "DIRECT", by, null);
    }

    @Transactional
    public void unassign(String memberId, String roleName, String projectId) {
        access.unassign(memberId, roleName, placeFor(roleName, projectId));
    }

    /**
     * Where a role of this name may be assigned, given what the caller asked for.
     *
     * <p>Both mistakes are refused rather than quietly corrected. Handing a project role out globally is
     * a widening nobody asked for; pinning a global role to one project is a grant that confers nothing
     * where the caller expected it to confer everything, which is worse for being invisible.
     */
    private ScopeReference placeFor(String roleName, String projectId) {
        AccessAdministration.RoleView role = requireRole(roleName);
        boolean                       global = TesseraScope.GLOBAL.name().equals(role.assignableAt());

        if (global && projectId != null) {
            throw new BusinessRuleViolationException(
                    "'" + roleName + "' is assignable installation-wide, so it cannot be given inside a "
                    + "single project — held there it would confer nothing.");
        }

        if (!global && (projectId == null || projectId.isBlank())) {
            throw new BusinessRuleViolationException(
                    "'" + roleName + "' is assignable at a project, so it needs one. Granted "
                    + "installation-wide it would apply to every project there will ever be.");
        }

        return global ? Targets.installationScope() : Targets.projectScope(projectId);
    }

    // ── What one person holds personally ──────────────────────────────────────

    /**
     * One permission handed to, or taken from, one person — over the top of whatever their roles say.
     *
     * <p>⚠️ <strong>A deny is the only way to take something away without editing the role that gives
     * it.</strong> It wins over every allow from anywhere, so this is the sharpest instrument on the
     * screen and the reason {@code reason} is required rather than optional.
     */
    @Transactional
    public void grant(
            String memberId, String permission, boolean allowed, String projectId, String reason,
            String by) {

        members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        requireKnownPermission(permission);

        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolationException(
                    "A personal grant needs a reason. It outlives whoever made it, and one nobody can "
                    + "explain is a refusal somebody will spend an afternoon on a year from now.");
        }

        access.grant(memberId, permission, placeOf(projectId),
                allowed ? AccessAdministration.Effect.ALLOW : AccessAdministration.Effect.DENY,
                reason, by, null);
    }

    @Transactional
    public void ungrant(String memberId, String permission, String projectId) {
        access.ungrant(memberId, permission, placeOf(projectId));
    }

    /**
     * ⚠️ <strong>A personal grant may be made at either floor, unlike a role assignment.</strong> A role
     * carries a scope kind that decides where it means anything; a bare permission does not, so "this
     * person may do X in this project" and "this person may do X anywhere" are both sentences somebody
     * might legitimately want to write.
     */
    private ScopeReference placeOf(String projectId) {
        return projectId == null || projectId.isBlank()
                ? Targets.installationScope()
                : Targets.projectScope(projectId);
    }

    private AccessAdministration.RoleView requireRole(String roleName) {
        return access.roles().stream()
                .filter(role -> role.name().equals(roleName))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
    }

    private void requireKnownPermission(String permission) {
        if (!Permissions.all().contains(permission)) {
            throw new BusinessRuleViolationException(
                    "'" + permission + "' is not a permission this build knows about, so granting it "
                    + "would confer nothing and denying it would refuse nothing.");
        }
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    private List<PermissionView> permissions() {
        Map<String, String> described = document.permissions().stream()
                .collect(Collectors.toMap(
                        PolicyPermissionDeclaration::name,
                        declaration -> declaration.description() == null ? "" : declaration.description(),
                        (first, second) -> first));

        return Permissions.all().stream()
                .map(name -> new PermissionView(name, described.get(name)))
                .toList();
    }

    private List<RoleView> roles() {
        return access.roles().stream()
                .map(role -> viewOf(role.name(), role.assignableAt(), role.bundle().stream()
                        .map(entry -> new BundleEntryView(entry.permission(), entry.scopeType()))
                        .toList()))
                .sorted(Comparator.comparing(RoleView::name))
                .toList();
    }

    private List<RoleHoldingView> roleHoldings(
            Map<String, MemberSummary> whoIsWho, Map<String, ProjectRef> whereIsWhere) {

        return disclosure.roleHoldings().stream()
                .map(holding -> new RoleHoldingView(
                        whoIsWho.get(holding.subjectId()),
                        holding.roleName(),
                        holding.at().type().name(),
                        projectOf(holding.at(), whereIsWhere),
                        holding.grantedBy(),
                        holding.since()))
                .sorted(byMemberThenRole())
                .toList();
    }

    private List<DirectHoldingView> directHoldings(
            Map<String, MemberSummary> whoIsWho, Map<String, ProjectRef> whereIsWhere) {

        return disclosure.directHoldings().stream()
                .map(holding -> new DirectHoldingView(
                        whoIsWho.get(holding.subjectId()),
                        holding.permission(),
                        holding.allowed(),
                        holding.at().type().name(),
                        projectOf(holding.at(), whereIsWhere),
                        holding.reason(),
                        holding.since()))
                .toList();
    }

    // ── ─────────────────────────────────────────────────────────────────────

    /**
     * ⚠️ A bundle entry naming a permission nobody declares, or a scope nobody registers, is refused at
     * the write rather than found later.
     *
     * <p>It would otherwise be stored, read back onto the screen, and confer nothing — the exact failure
     * {@code DeclaredPolicyValidator} exists to stop in the document, arriving through the editor
     * instead.
     */
    private void requireItCanMatchSomething(BundleEntryView entry) {
        if (!Permissions.all().contains(entry.permission())) {
            throw new BusinessRuleViolationException(
                    "'" + entry.permission() + "' is not a permission this build knows about, so a role "
                    + "carrying it would confer nothing.");
        }

        if (scopes.byName(entry.carriedAt()).isEmpty()) {
            throw new BusinessRuleViolationException(
                    "'" + entry.carriedAt() + "' is not a scope this build registers. The scopes are "
                    + registeredScopes() + ".");
        }
    }

    private String registeredScopes() {
        return scopes.floors().stream().map(ScopeKind::name).collect(Collectors.joining(", "));
    }

    /** Whether {@code policy/tessera.jmp} declares this role — see the class note on what a re-seed does. */
    private RoleView viewOf(String name, String assignableAt, List<BundleEntryView> bundle) {
        Set<String> declared = document.roles().stream()
                .map(PolicyRole::name)
                .collect(Collectors.toSet());

        return new RoleView(name, assignableAt, declared.contains(name), bundle);
    }

    /** The project a scope reference is about, or null where it is about the installation. */
    private static ProjectRef projectOf(ScopeReference at, Map<String, ProjectRef> whereIsWhere) {
        return TesseraScope.PROJECT.equals(at.type()) ? whereIsWhere.get(at.id()) : null;
    }

    private static ProjectRef referenceOf(Project project) {
        return new ProjectRef(project.getId(), project.getKey(), project.getName());
    }

    /**
     * ⚠️ Sorted by a name that may be absent: a grant can outlive the account it was made to, because a
     * library table cannot foreign-key into this product's members. The screen shows those rows — they
     * are the orphans somebody has to clear — so the comparator has to survive one.
     */
    private static Comparator<RoleHoldingView> byMemberThenRole() {
        Function<RoleHoldingView, String> byMember =
                holding -> holding.member() == null ? "" : String.valueOf(holding.member().displayName());

        return Comparator.comparing(byMember, Comparator.nullsFirst(String::compareToIgnoreCase))
                .thenComparing(RoleHoldingView::roleName);
    }
}
