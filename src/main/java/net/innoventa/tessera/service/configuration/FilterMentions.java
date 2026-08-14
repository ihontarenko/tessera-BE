package net.innoventa.tessera.service.configuration;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.SavedFilter;
import net.innoventa.tessera.dto.configuration.FilterMention;
import net.innoventa.tessera.repository.SavedFilterRepository;
import net.innoventa.tessera.service.filter.BuiltInBoardFilters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Which filters would stop matching if a catalog row were renamed.
 *
 * <p>A filter compares on <strong>names</strong> — {@code issue.type.name == 'Bug'} is what the shipped
 * "Only bugs" toggle says — so renaming Bug does not break the filter in any way that shows. It goes on
 * parsing, goes on running, and quietly matches nothing. That is the failure this exists to put in front
 * of somebody before they press Save.
 *
 * <p>⚠️ <strong>Deliberately crude, and it reports rather than repairs.</strong> It is a substring search
 * for the quoted literal over a small shipped catalog and the saved-filter table, so a filter mentioning
 * "Bug" inside a description is a false positive. A false positive costs a glance; a miss costs a
 * silently broken filter, and the two are not the same size of mistake. Nothing here rewrites anybody's
 * expression: what somebody's filter means is theirs, and a rename is a poor moment to decide it for
 * them.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FilterMentions {

    private final BuiltInBoardFilters   builtInBoardFilters;
    private final SavedFilterRepository savedFilterRepository;

    /** Every filter whose expression contains {@code name} as a quoted literal, shipped ones first. */
    public List<FilterMention> of(String name) {
        List<FilterMention> mentions = new ArrayList<>();

        builtInBoardFilters.catalog().stream()
            .filter(filter -> mentions(filter.expression(), name))
            .forEach(filter -> mentions.add(new FilterMention(
                FilterMention.BUILT_IN, filter.id(), filter.label(), null, filter.expression())));

        savedFilterRepository.findAll().stream()
            .filter(filter -> mentions(filter.getExpression(), name))
            .forEach(filter -> mentions.add(new FilterMention(
                FilterMention.SAVED,
                filter.getId(),
                filter.getName(),
                filter.getProjectId(),
                filter.getExpression())));

        return List.copyOf(mentions);
    }

    /**
     * Both quotings, because jME accepts either and members write both.
     *
     * <p>Quoted rather than bare on purpose: a bare search for "Do" would match {@code 'To Do'},
     * {@code 'Done'} and the word {@code Done} in every description, which is enough noise to make the
     * warning worth ignoring — and a warning people ignore is worse than none.
     */
    private static boolean mentions(String expression, String name) {
        return expression != null
               && (expression.contains("'" + name + "'") || expression.contains("\"" + name + "\""));
    }

}
