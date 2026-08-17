package net.innoventa.tessera.config;

import net.innoventa.tessera.ai.AssistantPrompt;
import net.innoventa.tessera.security.Permissions;
import org.jmouse.ai.preferences.PreferenceDefinition;
import org.jmouse.ai.provider.ChatModel;
import org.jmouse.ai.provider.ProviderSettingsSource;
import org.jmouse.ai.provider.RoutingChatModel;
import org.jmouse.ai.spi.PermissionVocabulary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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

    /**
     * Every permission this build knows, so the catalogue refuses an action naming one that does not
     * exist.
     *
     * <p>Read off {@link Permissions}' constants rather than listed again — a second list beside them is
     * one commit behind from the day it is written. Which is exactly why this cannot be a property: the
     * whole value of it is that it is derived from the code the permissions are declared in.
     */
    @Bean
    public PermissionVocabulary aiPermissionVocabulary() {
        Set<String> declared = Arrays.stream(Permissions.class.getDeclaredFields())
                .filter(field -> field.getType() == String.class
                              && Modifier.isPublic(field.getModifiers())
                              && Modifier.isStatic(field.getModifiers()))
                .map(AiConfiguration::valueOf)
                .collect(Collectors.toCollection(TreeSet::new));

        return () -> declared;
    }

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

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException unreadable) {
            throw new IllegalStateException("Unable to read permission constant " + field.getName(),
                    unreadable);
        }
    }

}
