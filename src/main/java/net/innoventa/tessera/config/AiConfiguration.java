package net.innoventa.tessera.config;

import jakarta.persistence.EntityManagerFactory;
import net.innoventa.tessera.ai.AssistantPrompt;
import net.innoventa.tessera.repository.MemberRepository;
import net.innoventa.tessera.service.member.AgentMembers;
import org.jmouse.ai.agent.AgentDirectory;
import org.jmouse.ai.jpa.JpaAgentDirectory;
import org.jmouse.ai.preferences.PreferenceDefinition;
import org.jmouse.ai.provider.ChatModel;
import org.jmouse.ai.provider.ProviderSettingsSource;
import org.jmouse.ai.provider.RoutingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * The two things about {@code jmouse-ai} that are genuinely Tessera's.
 *
 * <h2>⚠️ This file used to be four hundred lines, and every one of them was the starter's</h2>
 *
 * <p>It declared the guards, the chain, the catalogue, the dispatcher, the counters, the confirmation
 * store, the provider pair, the agent pair, seven controllers, a Flyway migrator and a
 * {@code BeanFactoryPostProcessor} to order it — and it opened by explaining that a starter could not
 * be used because this product was on Spring Boot 3.
 *
 * <p><strong>It has not been on Boot 3 for some time.</strong> The sentence was copied from the product
 * that wrote this arrangement first — which really is on Boot 3 — and it went on being true of that
 * product and quietly false here. Meanwhile the other Boot 4 product took
 * {@code jmouse-ai-spring-boot} and wrote none of it. Four hundred lines of hand-wiring were
 * maintained on the strength of a stale comment.
 *
 * <p>What replaces them is a dependency and a properties block. Everything the starter contributes is
 * {@code @ConditionalOnMissingBean}, so the beans below still win — and the seams that decide something
 * are {@code @Component}s in {@code ai/} and {@code security/access/} that the starter steps aside for
 * without either side naming the other.
 *
 * <h2>What stayed, and why each one could not move</h2>
 *
 * <p>⚠️ <strong>Everything else that looked like configuration became configuration.</strong> The guard
 * thresholds, the rate limit, the required-guard roster and the conversation budget are all
 * {@code jmouse.ai.*} properties now — they were numbers in Java only because there was nothing binding
 * them.
 */
@Configuration
public class AiConfiguration {

    // ⚠️ THERE IS NO `PermissionVocabulary` BEAN HERE, AND THAT IS THE FIX RATHER THAN AN OMISSION.
    // `AiAccessAutoConfiguration` already contributes one as `permissions::all` over the engine's
    // `PermissionCatalog`, behind . This file used to declare its own by
    // reflecting over `Permissions`' constants, which SHADOWED that bridge — so the tool library and
    // the access engine ended up with two different ideas of the vocabulary, and adding the `tool:`
    // axis to one of them failed the boot with twenty-four permissions that "do not exist" while they
    // sat declared in `policy/tools.jmp`. Deleting the bean is what makes the two the same list, and
    // `AccessVocabularyConfiguration` reading that list off the policy documents is what makes it one
    // source rather than two.

    /**
     * A model resolved per turn from whichever row is in force.
     *
     * <p>⚠️ <strong>Unconditional, unlike the starter's.</strong> {@code AiProviderAutoConfiguration}
     * builds a {@link ChatModel} only where {@code jmouse.ai.provider.name} is set — and that property
     * is deliberately absent here, because naming the provider in a file is the one thing that cannot
     * then be changed on a screen. This bean reads the provider from the same row that carries the model
     * and the key.
     *
     * <p>An installation with no active row still starts perfectly and simply has no assistant: the
     * refusal comes from the settings source at the moment somebody asks, and {@code AssistantService}
     * asks {@code ProviderRegistry} rather than this bean whether there is anything to ask.
     */
    @Bean
    public ChatModel aiChatModel(ProviderSettingsSource settingsSource) {
        return RoutingChatModel.overShippedProviders(settingsSource);
    }

    /**
     * The library's agent directory, with a <strong>member row mirroring every agent</strong> (TSSR-32).
     *
     * <h2>⚠️ Built here rather than annotated where it lives</h2>
     *
     * <p>{@code jmouse-ai-spring-boot} registers its own {@link AgentDirectory} behind
     * {@code @ConditionalOnMissingBean}. A {@code @Primary @Component} wrapper would therefore break in
     * two ways at once: the component is registered <em>before</em> autoconfiguration runs, so the
     * library would see a directory already present and <strong>skip creating the one the wrapper
     * wraps</strong>; and asking for the interface in its constructor would resolve to itself.
     *
     * <p>Declaring the bean here answers both. There is exactly one {@code AgentDirectory} in the
     * context, the library's condition is satisfied by it, and the delegate is <em>constructed</em>
     * rather than looked up — so there is nothing for Spring to resolve ambiguously. Innoventa's
     * {@code AiToolConfiguration} carries the same explanation and is where this shape came from.
     *
     * <p>⚠️ <strong>Its absence would be silent.</strong> Nothing fails when the mirror stops being
     * written; agents simply stop having faces, and the by-lines on everything they write go back to
     * being unresolvable identifiers — noticed months later by whoever wonders who wrote something.
     */
    @Bean
    public AgentDirectory agentDirectory(
            EntityManagerFactory entityManagerFactory,
            MemberRepository memberRepository) {

        return new AgentMembers(new JpaAgentDirectory(entityManagerFactory), memberRepository);
    }

    /**
     * The assistant's prompt, declared as something an installation may rewrite.
     *
     * <p>⚠️ <strong>The shipped wording is still in {@link AssistantPrompt}</strong> — this declares it
     * as the <em>default</em> of a preference, so a row in {@code ai_preferences} overrides it and an
     * installation with no row behaves exactly as this build was released. Nothing is seeded.
     */
    @Bean
    public PreferenceDefinition assistantSystemPrompt() {
        return AssistantPrompt.definition();
    }

}
