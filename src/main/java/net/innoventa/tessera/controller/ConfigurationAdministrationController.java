package net.innoventa.tessera.controller;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.dto.configuration.FilterMention;
import net.innoventa.tessera.security.Permissions;
import net.innoventa.tessera.security.access.Scopes;
import net.innoventa.tessera.service.configuration.ConfigurationUsage;
import net.innoventa.tessera.service.configuration.ConfigurationUsage.ConfigurationCounts;
import net.innoventa.tessera.service.configuration.FilterMentions;
import net.innoventa.tessera.service.configuration.IssueTypeIcons;
import org.jmouse.access.enforcement.RequiresAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Administration screen's own reads — the facts about the configuration that
 * {@code GET /api/configuration} does not carry because nothing else needs them.
 *
 * <p>⚠️ <strong>Open to any signed-in caller, like every other configuration read.</strong> A member
 * without {@code configuration:administer} who reaches {@code /administration} by URL sees the screens
 * read-only rather than an error, which needs the numbers on them to load. Nothing here discloses
 * anything a member cannot already reach — an issue count per status is a tally of issues they may
 * browse — and gating it would make "read-only" mean "half of it is missing".
 *
 * <p>The writes live on a controller per catalog family, each gating its own routes on
 * {@code configuration:administer} at {@code GLOBAL}.
 */
@RestController
@RequestMapping("/api/admin/configuration")
@RequiredArgsConstructor
@RequiresAccess
public class ConfigurationAdministrationController {

    private final ConfigurationUsage configurationUsage;
    private final FilterMentions     filterMentions;

    /**
     * How many issues (or links) each catalog row holds, in one request.
     *
     * <p>The screen puts a number beside every status and every issue type at once. A usage call per row
     * would be the same answer and sixty round trips.
     */
    @GetMapping("/counts")
    public ConfigurationCounts counts() {
        return configurationUsage.counts();
    }

    /**
     * Which filters mention a name as a literal — the warning shown before a rename is offered.
     *
     * <p>One route for every catalog, because the answer depends only on the word: a filter comparing
     * {@code issue.type.name == 'Bug'} does not record that Bug is an issue type, so scanning for the
     * quoted literal is the whole search whatever kind of row is being renamed.
     *
     * <p>⚠️ <strong>Gated, unlike the reads above, because it discloses rather than counts.</strong> It
     * scans every saved filter in the installation and answers with their names, projects and full
     * expressions — somebody's private filter in a project the caller cannot browse included. A count
     * beside a status tells a member nothing they could not already work out; this would.
     */
    @GetMapping("/rename-impact")
    @RequiresAccess(permission = Permissions.ADMINISTER_CONFIGURATION, scope = Scopes.GLOBAL)
    public List<FilterMention> renameImpact(@RequestParam("name") String name) {
        return filterMentions.of(name);
    }

    /** The icon keys the client draws, for the issue-type picker — never free text. */
    @GetMapping("/issue-type-icons")
    public List<String> issueTypeIcons() {
        return IssueTypeIcons.ALL;
    }

}
