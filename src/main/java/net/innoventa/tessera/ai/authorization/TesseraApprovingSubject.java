package net.innoventa.tessera.ai.authorization;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.service.MemberService;
import org.jmouse.ai.agent.Agent;
import org.jmouse.ai.agent.AgentAuthority;
import org.jmouse.ai.agent.AgentConnections;
import org.jmouse.ai.agent.AgentDirectory;
import org.jmouse.ai.mcp.authorization.McpAuthorizationException;
import org.jmouse.ai.mcp.authorization.server.ApprovingSubject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Who is approving, and which of their agents a client would become.
 *
 * <h2>⚠️ This used to answer with the member and nothing else, and that was half a model</h2>
 *
 * <p>The sentence it carried was that Tessera has <em>no service sub-accounts at all</em> — a connected
 * client acted as the person who approved it, there was nothing to choose between, and the shared screen
 * rendered no picker. That stopped being true when an agent became a row: a person here owns agents, with
 * their own authority, their own switch and their own connections, exactly as in the other product.
 *
 * <p>What survived from it was a screen that could only ever attach a client to whichever persona the
 * enrolment rule happened to land on. Two clients under one agent, or one agent per client, was decided
 * by a match on a registration identifier — never by the person, who could see neither outcome coming.
 * Meanwhile the other product asked them outright. Same screen, same library, opposite amounts of
 * control.
 *
 * <p>So the list is the agents this member owns, plus {@link ApprovingSubject#newSubject} for the one
 * that does not exist yet. A member with no agents therefore sees a single choice and no picker — which
 * is what the old screen looked like, and is now the <em>empty case</em> of one model rather than a
 * different model.
 *
 * <p>⚠️ <strong>A disabled agent is listed rather than hidden.</strong> Somebody who switched one off and
 * forgot needs to see why the client cannot use it; an agent that silently vanishes from a list reads as
 * an agent that was deleted, and the next thing that happens is a second one being created.
 */
@Component
@RequiredArgsConstructor
public class TesseraApprovingSubject implements ApprovingSubject {

    /** What a first connection is offered when this member has no agent to point it at yet. */
    private static final String NEW_AGENT_NAME = "A new agent for this client";

    private static final String NEW_AGENT_DETAIL =
            "Created when you approve, named after the client, and able to do everything you can. "
            + "Narrow it or switch it off afterwards on the agents screen.";

    private final MemberService    memberService;
    private final AgentDirectory   agents;
    private final AgentConnections connections;

    @Override
    public Approver current() {
        Member member = memberService.resolveMember(signedInToken());

        List<Choice> choices = new ArrayList<>(
                agents.ownedBy(member.getId()).stream()
                        .map(this::asChoice)
                        .toList());

        choices.add(ApprovingSubject.newSubject(member.getId(), NEW_AGENT_NAME, NEW_AGENT_DETAIL));

        return new Approver(member.getDisplayName(), member.getEmail(), List.copyOf(choices));
    }

    /** An agent switched off is a decision already taken; the screen shows it and will not take it. */
    private Choice asChoice(Agent agent) {
        if (agent.enabled()) {
            return Choice.of(agent.id(), agent.name(), describe(agent));
        }

        return new Choice(agent.id(), agent.name(), describe(agent), false,
                "'" + agent.name() + "' is switched off, so no credential can be issued for it. Turn it "
                + "back on from the agents screen, or choose another.");
    }

    /**
     * The two things worth knowing before pointing a client at an existing agent: how much it may do, and
     * whether anything is already connected to it.
     *
     * <p>The second is what makes the choice a real one — attaching a laptop to the persona a desktop
     * already uses means one permission set and one switch for both, which is sometimes exactly right and
     * sometimes the opposite, and only the person deciding knows which.
     */
    private String describe(Agent agent) {
        int connected = connections.of(agent.id()).size();

        String authority = agent.authority() == AgentAuthority.INHERITED
                ? "Full access · everything you can do"
                : "Restricted · only what it has been granted";

        if (connected == 0) {
            return authority + " · nothing connected yet";
        }

        return authority + " · " + connected + (connected == 1 ? " client connected" : " clients connected");
    }

    /**
     * ⚠️ Read from the security context rather than a controller argument, because the endpoint asking is
     * the library's and it has no business taking a {@code Jwt} parameter. The chain has already refused
     * anybody who is not signed in, so the failure below is a wiring mistake rather than a caller's.
     */
    private Jwt signedInToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
            throw new McpAuthorizationException(
                    "Only somebody signed in to Tessera can approve a client, and this request is not.");
        }

        return token;
    }
}
