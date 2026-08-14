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
 * Checks {@code policy/tessera.jmp} against {@link Permissions} in <strong>both</strong> directions, and
 * stops the application where they disagree.
 *
 * <p>This is the whole reason writing authorization down is safe rather than merely tidy. A permission
 * is a bare string wherever it is asked about, so a document writing {@code BROWSE_PROJEKT} would load
 * cleanly, bind cleanly, seed a role that carries it, and grant absolutely nothing — with no log line
 * anywhere saying so. Nobody finds that until somebody cannot see a project they are a member of.
 *
 * <p>Both directions, because the two failures are different and both matter:
 *
 * <ul>
 *   <li><strong>Declared and unknown.</strong> A name in the file that no constant matches — a typo, or
 *       a permission somebody deleted from the code and forgot in the document. It grants nothing, and
 *       it is the dangerous one.
 *   <li><strong>Known and undeclared.</strong> A constant no line declares. It is not dangerous — the
 *       engine never looks a permission up — but it means the document has stopped being the vocabulary,
 *       and a document that is only <em>most</em> of the truth is one nobody trusts enough to read.
 * </ul>
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

        Set<String> known = new TreeSet<>(Permissions.all());

        List<String> unknown = declared.stream().filter(name -> !known.contains(name)).toList();
        List<String> missing = known.stream().filter(name -> !declared.contains(name)).toList();

        if (unknown.isEmpty() && missing.isEmpty()) {
            return;
        }

        throw new IllegalStateException(complaint(unknown, missing));
    }

    private String complaint(List<String> unknown, List<String> missing) {
        StringBuilder complaint = new StringBuilder(
                "The policy document '" + document.name() + "' and Permissions disagree about what a "
                + "permission is called, and a permission is a bare string everywhere it is asked "
                + "about — so a name only one of them knows grants nothing, silently.\n");

        if (!unknown.isEmpty()) {
            complaint.append("\n  Declared in the document and matching no constant: ")
                    .append(String.join(", ", unknown))
                    .append("\n  Either it is a typo, or the constant was deleted and the line was not.");
        }

        if (!missing.isEmpty()) {
            complaint.append("\n\n  A constant no line declares: ")
                    .append(String.join(", ", missing))
                    .append("\n  Add it to the 'declare permissions' block — the document is the "
                            + "vocabulary, and one that is only most of the truth is one nobody reads.");
        }

        return complaint.toString();
    }
}
