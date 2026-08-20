package net.innoventa.tessera.dto;

import net.innoventa.tessera.domain.Member;

/**
 * The compact projection of a {@link Member} embedded wherever a person is referenced — project
 * lead, membership rows, issue assignee/reporter, comment author. Carries only what the UI
 * needs to render a name/avatar, never the raw subject in bulk listings.
 *
 * <p>⚠️ <strong>This factory is the single funnel.</strong> Fourteen response types embed a
 * {@code MemberSummary} and every one of them is built by calling {@link #from(Member)} — so a field
 * added here reaches all of them at once and no call site changes. That is why {@code avatar} is here
 * rather than only on {@code CurrentMemberResponse}: a face nobody but you can see is not a face.
 */
public record MemberSummary(
    String id,
    String displayName,
    String email,
    MemberAvatarView avatar,
    /**
     * Whether this is a person or a client (TSSR-34, TSSR-36).
     *
     * <p>⚠️ <strong>This is the field that replaced `agentName` on two DTOs</strong>, and it is why the
     * funnel was worth having. Provenance used to be a bare string carried beside the author on
     * {@code comments} and {@code activity_logs}, rendered as a badge glued next to somebody else's
     * chip. Now the author <em>is</em> the agent, so a client arrives with a name, a face and this — and
     * the fourteen payloads got it without one of them changing.
     *
     * <p>⚠️ <strong>An offer to the interface, never a claim about authority.</strong> An agent carries
     * none; what a client may do is what the person who approved it may do, resolved live.
     */
    String kind,
    /**
     * Whose client it is, and null on a person.
     *
     * <p>⚠️ <strong>Record-keeping — the interface reads it to say "SU's client", nothing else may.</strong>
     * Resolving a permission through it would be a second permission model beside {@code jmouse-access}'s.
     * See the ADR.
     */
    String parentId,
    /** Whether the client behind it has been switched off. Everything it wrote keeps its name. */
    boolean retired
) {

    public static MemberSummary from(Member member) {
        return new MemberSummary(member.getId(), member.getDisplayName(), member.getEmail(),
                                 MemberAvatarView.from(member), member.getKind().name(),
                                 member.getParentId(), member.isRetired());
    }

}
