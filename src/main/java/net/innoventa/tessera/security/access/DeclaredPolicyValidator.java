package net.innoventa.tessera.security.access;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.security.Permissions;
import org.jmouse.access.policy.model.PolicyDocument;
import org.jmouse.access.policy.model.PolicyPermissionDeclaration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Checks that every permission this build asks with is declared, and stops the application otherwise.
 *
 * <p>This is the whole reason writing authorization down is safe rather than merely tidy. A permission
 * is a bare string wherever it is asked about, so a constant reading {@code BROWSE_PROJEKT} would
 * compile, be asked about on every request, match nothing, and refuse absolutely everybody — with no log
 * line anywhere saying so. Nobody finds that until somebody cannot see a project they are a member of.
 *
 * <h2>⚠️ One direction now, and the other one would be wrong</h2>
 *
 * <p>It used to check both, because the vocabulary existed twice — as constants and as declarations —
 * and either copy could drift from the other. The documents are now the vocabulary outright:
 * {@code PermissionCatalog} is read off them by the library, so <em>declared and matching no constant</em>
 * has stopped being litter and become the normal case. Every {@code tool:} permission in
 * {@code policy/tools.jmp} has no Java constant at all, because an action's permission is derived from
 * its own published name.
 *
 * <p>What is left is the half that still bites: a constant Java asks with that no line declares. It
 * exists because {@link Permissions}' constants are how services and {@code @RequiresAccess} name a
 * permission — a bare string at a call site is a typo waiting to grant nothing — and those constants are
 * the one part of the old arrangement that could not sensibly move into a file.
 *
 * <p>⚠️ <strong>{@link InitializingBean} rather than an event listener.</strong> The seed writes rows
 * from this document, so the check has to happen before anything can act on it — and a context that
 * refuses to finish starting is a clearer failure than one that starts and then logs.
 */
@Component
@RequiredArgsConstructor
public class DeclaredPolicyValidator implements InitializingBean {

    private final PolicyDocument document;

    @Override
    public void afterPropertiesSet() {
        Set<String> declared = document.permissions().stream()
                .map(PolicyPermissionDeclaration::name)
                .collect(Collectors.toCollection(TreeSet::new));

        // ⚠️ ONE DIRECTION NOW, AND THE OTHER ONE WOULD BE WRONG. The documents are the vocabulary —
        // `PermissionCatalog` is derived from them — so "declared but matching no constant" is no longer
        // litter, it is the normal case: every `tool:` permission is declared in `policy/tools.jmp` and
        // has no Java constant at all, because an action's permission is derived from its own name.
        // What still matters is the reverse: a constant Java asks with that the documents never declare
        // would be asked about forever and granted by nothing.
        List<String> missing = Permissions.all().stream()
                .filter(name -> !declared.contains(name))
                .toList();

        if (missing.isEmpty()) {
            return;
        }

        throw new IllegalStateException(complaint(missing));
    }

    private String complaint(List<String> missing) {
        return "The policy document '" + document.name() + "' does not declare every permission this "
             + "build asks with, and a permission is a bare string everywhere it is asked about — so a "
             + "name only the code knows is asked about forever and granted by nothing, silently.\n"
             + "\n  Asked with in Java and declared by no line: " + String.join(", ", missing)
             + "\n  Add it to a 'declare permissions' block. The documents are the vocabulary: "
             + "PermissionCatalog is read off them, so a name that is not there does not exist.";
    }
}
