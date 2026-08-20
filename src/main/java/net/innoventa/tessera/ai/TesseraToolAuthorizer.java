package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.security.access.ProjectAccess;
import org.jmouse.ai.CallerAttributes;
import org.jmouse.ai.CallerIdentity;
import org.jmouse.ai.InvocationScope;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.access.CallerSubjects;
import org.jmouse.ai.spi.ToolAuthorizer;
import org.springframework.stereotype.Component;

/**
 * Whether this caller has been given this tool.
 *
 * <h2>One tool, one permission, and the tool knows nothing else</h2>
 *
 * <p>Every action declares {@code tool:<published name>} and that is the whole of what reaching it
 * costs — {@code IssueTool.create()} asks for {@code tool:issues_create}, not for {@code issue:create}.
 * So administering an agent is <strong>ticking tools</strong>: open it, switch on all of them or a few,
 * and there is nothing else to fill in.
 *
 * <h2>⚠️ One subject is asked, and which one is the authority's answer</h2>
 *
 * <p>There is no second reading here and no special case: the question goes to
 * {@link CallerSubjects#of}, the same translation every other caller of the access engine uses. What
 * differs is who that turns out to be, and {@code AgentCallers} decided it before this ran.
 *
 * <table border="1">
 *   <caption>What each authority means</caption>
 *   <tr><th>Authority</th><th>Asked</th><th>Consequence</th></tr>
 *   <tr><td>{@code INHERITED}</td><td>the owner</td>
 *       <td>everything the owner holds, including their tool switches. ⚠️ An owner with no
 *           {@code tool:} permission gives the agent none — the agent is <em>being</em> them, so there
 *           is nothing of its own to consult</td></tr>
 *   <tr><td>{@code RESTRICTED}</td><td>the agent</td>
 *       <td>its own roles and permissions, <strong>not capped by the owner</strong>. A set the owner
 *           does not hold is a set the agent may still have</td></tr>
 * </table>
 *
 * <p>⚠️ <strong>So the two are different accounts rather than two points on one scale.</strong>
 * {@code INHERITED} is <em>be this person</em>; {@code RESTRICTED} is <em>be yourself</em>. A restricted
 * agent granted nothing is not a slightly narrowed owner — it holds nothing at all, sees no project, and
 * every call is refused. Filling it in is the point of restricting it.
 *
 * <h2>⚠️ Data is not this file's question, and never was</h2>
 *
 * <p>A tool permission says which action may be reached. <em>Which rows</em> is decided downstream by
 * the same subject — {@code ProjectScopeResolver} and every listing filter on its {@code project:browse}
 * — so a restricted agent also needs whatever ordinary permissions it is meant to work with, and an
 * inherited one has the owner's by construction.
 *
 * <h2>⚠️ Not scope-confined, and that is the design rather than an omission</h2>
 *
 * <p>The tool switches are global: <em>which</em> tools is this axis's answer, <em>where</em> they may
 * be pointed is the subject's ordinary project access. Confining the tool axis per project as well would
 * be a second place to say the same thing, and the two would disagree the first time somebody edited
 * one.
 */
@Component
@RequiredArgsConstructor
public class TesseraToolAuthorizer implements ToolAuthorizer {

    private final ProjectAccess projectAccess;

    @Override
    public boolean permits(CallerIdentity caller, ToolAction action) {
        return hasBeenGivenTheTool(caller, action);
    }

    /**
     * The same question, and deliberately the same answer.
     *
     * <p>The scope has already been resolved against the caller's own visible projects by the time this
     * runs — a project it cannot browse never became a scope at all — so asking the tool switch again
     * per project would add a condition that is either always true or a second, weaker copy of the
     * project access that has already been checked.
     */
    @Override
    public boolean permitsInScope(CallerIdentity caller, ToolAction action, InvocationScope scope) {
        return hasBeenGivenTheTool(caller, action);
    }

    private boolean hasBeenGivenTheTool(CallerIdentity caller, ToolAction action) {
        return projectAccess.holdsAnywhere(CallerSubjects.of(caller), action.requiredPermission());
    }

    /**
     * Why the tool is out of reach — for the one cause that reads as a broken installation and is not.
     *
     * <p>⚠️ <strong>A refusal here is never about a project.</strong> Before one permission per tool this
     * method explained that a caller belonging to no project holds no project permission — true then,
     * and actively misleading now: a refused caller may be pointed at every project in the installation
     * and simply not have this tool switched on. Sending somebody to fix their membership when the
     * answer is one checkbox is the expensive kind of wrong.
     */
    @Override
    public String refusalAdvice(CallerIdentity caller, ToolAction action, InvocationScope scope) {
        if (caller.attributes().get(CallerAttributes.AGENT_ID) == null) {
            return "This account has not been given this tool. Tool permissions are one switch per "
                 + "action, so nothing about projects or membership is involved here.";
        }

        return "This agent has not been given this tool. Every tool is its own switch, and "
             + "MCP_AGENT_TOOLS turns the ordinary set on at once. ⚠️ Which switches are read depends on "
             + "the agent's authority: an agent following its owner is asked about THE OWNER'S "
             + "permissions, so switch them on there; a restricted agent is asked about its own.";
    }
}
