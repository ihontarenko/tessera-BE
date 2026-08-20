package net.innoventa.tessera.service.member;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.domain.MemberKind;
import net.innoventa.tessera.domain.SystemRole;
import net.innoventa.tessera.repository.MemberRepository;
import org.jmouse.ai.agent.Agent;
import org.jmouse.ai.agent.AgentAuthority;
import org.jmouse.ai.agent.AgentDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The library's agent directory, with a <strong>member row mirroring every agent</strong> (TSSR-32).
 *
 * <h2>Why the mirror exists at all</h2>
 *
 * <p>Provenance used to be a pair of columns beside the author — {@code agent_id} and
 * {@code agent_name} on {@code comments} and on {@code activity_logs}. That works and does not compose:
 * every future table an agent can write repeats the pair, {@code agent_name} is a snapshot that goes
 * stale on a rename, and the agent has no face — {@code MemberSummary.from(Member)} is the single funnel
 * fourteen response types are built through, and an agent reached none of it.
 *
 * <p>With a mirror, <em>who wrote this</em> is one reference again, resolved through the funnel that
 * already exists, with an avatar and a name that is current rather than copied.
 *
 * <h2>⚠️ The mirror carries no authority, and its {@code parent_id} is never read to decide one</h2>
 *
 * <p>Authorization is settled before any of this: {@code AgentCallers} reads {@code AgentAuthority}
 * exactly once and hands everything downstream a plain {@code CallerIdentity}, capped by
 * {@code jmouse-access}'s intersection. This row is <strong>record-keeping</strong>.
 *
 * <p>⚠️ The trap is that {@code parent_id} <em>looks</em> like an inheritance edge, and it is at its most
 * tempting right after this migration lands, when a parented row is sitting there looking like the
 * answer. WiQi got it wrong twice — first claiming the rule held "structurally" because nothing there
 * could break it, then "fixing" that by inventing a second owner column which was the library's
 * {@code ownerReference()} under a new name. Read the ADR before touching either.
 *
 * <h2>⚠️ Declared as a {@code @Bean}, never as a {@code @Primary @Component}</h2>
 *
 * <p>{@code jmouse-ai-spring-boot} registers its directory behind {@code @ConditionalOnMissingBean}. A
 * component wrapper is registered <em>before</em> autoconfiguration runs, so the library would see a
 * directory already present and <strong>skip creating the one being wrapped</strong> — and asking for
 * the interface in the constructor would then resolve to this class itself.
 */
@RequiredArgsConstructor
public class AgentMembers implements AgentDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentMembers.class);

    /**
     * ⚠️ <strong>The prefix Identity can never mint, and that is the whole safety argument.</strong>
     *
     * <p>{@code members.subject} stays {@code NOT NULL} and unique because it is the key
     * {@code MemberService.resolveMember(Jwt)} looks a member up by. An agent has no Identity
     * {@code sub}, so its mirror gets a synthetic one rather than the column becoming nullable — the
     * constraint survives, and {@code requireBySubject} is <em>provably</em> unable to answer with an
     * agent. Nullable would have weakened the one invariant saying <em>a member is somebody Identity
     * knows</em>, on behalf of the rows that are precisely the exception.
     */
    public static final String SUBJECT_PREFIX = "agent:";

    private final AgentDirectory   directory;
    private final MemberRepository memberRepository;

    // ── The half that is this product's ──────────────────────────────────────────

    /**
     * Records the agent, then mirrors it as a member.
     *
     * <p>⚠️ <strong>The mirror's identifier is the agent's own</strong>, which is what makes an
     * {@code author_member_id} and a token's {@code aid} claim the same string — so nothing translates
     * between them and there is no second identifier to get out of step.
     *
     * <p>⚠️ <strong>Its face is generated from that identifier, never borrowed from its owner.</strong>
     * Two agents of one person are told apart at a glance, and a comment written by a client does not
     * wear the face of the person who was asleep at the time. A preset seed is drawn from rather than
     * looked up — the generator is total — so any identifier is a valid seed and nothing needs seeding.
     *
     * <p>⚠️ <strong>{@code SystemRole.USER}, never the owner's tier.</strong> Copying a tier would be a
     * second answer to "what may this do", sitting on a row the access core is not allowed to read.
     */
    @Override
    @Transactional
    public Agent register(Draft draft) {
        Agent agent = directory.register(draft);

        Member mirror = Member.builder()
                .id(agent.id())
                .subject(SUBJECT_PREFIX + agent.id())
                .displayName(agent.name())
                .kind(MemberKind.AGENT)
                .parentId(agent.ownerReference())
                .systemRole(SystemRole.USER)
                .build();

        mirror.wearsPreset(agent.id());
        memberRepository.save(mirror);

        LOGGER.info("Agent '{}' ({}) registered for member {}, and mirrored as the author of anything "
                    + "it writes.", agent.name(), agent.id(), agent.ownerReference());

        return agent;
    }

    /** ⚠️ The mirror follows, so a by-line says what the agent is called now rather than what it was. */
    @Override
    @Transactional
    public Agent rename(String agentId, String name) {
        Agent renamed = directory.rename(agentId, name);

        mirrorOf(agentId).ifPresent(mirror -> {
            mirror.setDisplayName(name);
            memberRepository.save(mirror);
        });

        return renamed;
    }

    /**
     * Discards the agent — and <strong>retires</strong> its mirror rather than deleting it.
     *
     * <h2>⚠️ The non-negotiable of the whole epic</h2>
     *
     * <p>History has to outlive what it names. A comment and an activity entry <em>point</em> at the
     * mirror, so removing it would lose the author of everything the agent ever produced — silently, at
     * the moment somebody tidies up their connections. Discarding the {@code ai_agents} row is right and
     * takes its connections with it; discarding the mirror would be data loss disguised as a cleanup.
     *
     * <p>⚠️ <strong>The two halves are asymmetric on purpose.</strong> The library row is about what an
     * agent may do next, and there is nothing left to say. The mirror is about what it already did, and
     * that does not stop being true.
     */
    @Override
    @Transactional
    public void discard(String agentId) {
        directory.discard(agentId);
        retireMirror(agentId);
    }

    /**
     * ⚠️ <strong>Retire first, then discard.</strong> The owner's agents are read from the library, so
     * discarding them first would leave nothing to enumerate and every mirror standing live behind a
     * directory that no longer knows them.
     */
    @Override
    @Transactional
    public void discardAllOwnedBy(String ownerReference) {
        directory.ownedBy(ownerReference).forEach(agent -> retireMirror(agent.id()));
        directory.discardAllOwnedBy(ownerReference);
    }

    /**
     * The mirror behind an agent identifier, for whoever needs a face rather than a permission.
     *
     * <p>⚠️ <strong>Filtered on the kind rather than looked up bare.</strong> An identifier arriving in a
     * token's {@code aid} claim is a string, and a member identifier that happened to name a
     * <em>person</em> would otherwise resolve — attributing a comment to somebody who never wrote it.
     */
    @Transactional(readOnly = true)
    public Optional<Member> mirrorOf(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }

        return memberRepository.findByIdAndKind(agentId, MemberKind.AGENT);
    }

    /** Every mirror belonging to this person — what "mine" widens to (TSSR-35). */
    @Transactional(readOnly = true)
    public List<Member> mirrorsOwnedBy(String ownerMemberId) {
        return memberRepository.findByKindAndParentId(MemberKind.AGENT, ownerMemberId);
    }

    private void retireMirror(String agentId) {
        mirrorOf(agentId).ifPresent(mirror -> {
            mirror.retire();
            memberRepository.save(mirror);

            LOGGER.info("Agent '{}' ({}) was discarded. Its member row is retired rather than deleted — "
                        + "nothing it wrote loses its author.", mirror.getDisplayName(), agentId);
        });
    }

    // ── Everything else is the library's, untouched ──────────────────────────────

    @Override
    public Optional<Agent> find(String agentId) {
        return directory.find(agentId);
    }

    @Override
    public List<Agent> ownedBy(String ownerReference) {
        return directory.ownedBy(ownerReference);
    }

    @Override
    public List<Agent> all(int limit) {
        return directory.all(limit);
    }

    @Override
    public Agent actWith(String agentId, AgentAuthority authority) {
        return directory.actWith(agentId, authority);
    }

    @Override
    public Agent putInService(String agentId) {
        return directory.putInService(agentId);
    }

    @Override
    public Agent takeOutOfService(String agentId) {
        return directory.takeOutOfService(agentId);
    }

    @Override
    public void stampActive(String agentId, Instant at) {
        directory.stampActive(agentId, at);
    }
}
