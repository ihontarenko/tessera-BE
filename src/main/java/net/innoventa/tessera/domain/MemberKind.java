package net.innoventa.tessera.domain;

/**
 * What a {@link Member} row actually is (TSSR-32).
 *
 * <h2>⚠️ A column, not {@code parent_id IS NULL}</h2>
 *
 * <p>The two say different things — <em>this is an agent</em> and <em>this has a parent</em> — and the
 * moment anything else wants a sub-member (an imported author, a webhook identity, a service
 * integration) the inference is ambiguous and every query that guessed is wrong. A column makes the
 * question a {@code WHERE} clause somebody can read.
 *
 * <h2>⚠️ One entity, never a JPA subtype</h2>
 *
 * <p>Decided 2026-08-17. An {@code @Inheritance} hierarchy makes every existing {@code Member} query
 * polymorphic and hands an agent back wherever a person was meant, with nothing to warn about it. One
 * table, one entity, one column — and {@code AgentMembers} owning the concept so the rules are stated
 * once.
 */
public enum MemberKind {

    /** Somebody Identity knows. Their {@code subject} is a real {@code sub} claim. */
    PERSON,

    /**
     * A client's standing identity — the name and face on everything it writes.
     *
     * <p>⚠️ <strong>It carries no authority whatsoever.</strong> Authorization is settled before any of
     * this, from {@code AgentAuthority} read once per call; this row is <em>record-keeping</em>. Its
     * {@code parent_id} is who it belongs to and is never read by anything deciding whether a call is
     * allowed — see the ADR, and see the two ways WiQi got that wrong before writing it down.
     */
    AGENT

}
