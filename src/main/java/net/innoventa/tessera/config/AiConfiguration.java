package net.innoventa.tessera.config;

import net.innoventa.tessera.security.Permissions;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.jmouse.ai.GuardRoster;
import org.jmouse.ai.ToolCatalog;
import org.jmouse.ai.ToolDefinition;
import org.jmouse.ai.ToolDispatcher;
import org.jmouse.ai.guard.CeilingGuard;
import org.jmouse.ai.guard.ConfirmationGuard;
import org.jmouse.ai.guard.DeduplicationGuard;
import org.jmouse.ai.guard.EmptyDestructionGuard;
import org.jmouse.ai.guard.GuardChain;
import org.jmouse.ai.guard.GuardSettings;
import org.jmouse.ai.guard.InMemoryDuplicateCallStore;
import org.jmouse.ai.guard.RateLimitGuard;
import org.jmouse.ai.guard.TokenBucketCallerRateLimiter;
import org.jmouse.ai.jpa.JpaConfirmationStore;
import org.jmouse.ai.jpa.migration.AiDialect;
import org.jmouse.ai.jpa.migration.AiMigrations;
import org.jmouse.ai.provider.AnthropicChatModel;
import org.jmouse.ai.provider.ChatModel;
import org.jmouse.ai.provider.GatewayChatModel;
import org.jmouse.ai.provider.OpenAiChatModel;
import org.jmouse.ai.provider.ProviderSettings;
import org.jmouse.ai.provider.ProviderSettingsSource;
import org.jmouse.ai.spi.CallerResolver;
import org.jmouse.ai.spi.ConfirmationStore;
import org.jmouse.ai.spi.InvocationTrace;
import org.jmouse.ai.spi.PermissionVocabulary;
import org.jmouse.ai.spi.ScopeResolver;
import org.jmouse.ai.spi.ToolAuthorizer;
import org.jmouse.ai.conversation.ConversationBudget;
import org.jmouse.ai.conversation.ConversationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * <strong>{@code jmouse-ai} on Spring Boot 3, wired by hand — the second time.</strong>
 *
 * <p>Central wrote this file first, as the worked example the arrangement promises; this is the test
 * of whether the example transfers. It does, and the differences are all Tessera's own: a project is
 * the scope rather than an application, there is a tool catalogue here where Central had only a
 * provider, and the confirmation store is the library's JPA one rather than Redis because Tessera has
 * no Redis.
 *
 * <p><strong>What has to be declared, and why each one.</strong> The starter would do all of it; on
 * Boot 3 there is no starter and deliberately will not be one, because an auto-configuration spanning
 * two major versions of Boot is how a library ends up owning a compatibility matrix.
 *
 * <ol>
 *   <li><strong>The seams that decide something</strong> — who the caller is, what a permission means,
 *       what a project is. Those are {@code @Component}s in {@code ai/} and only appear here as
 *       constructor parameters.
 *   <li><strong>The guards</strong>, and the chain over them.
 *   <li><strong>The catalogue and the dispatcher.</strong>
 *   <li><strong>A model</strong>, if one is configured — and nothing at all if not, which is a
 *       supported arrangement rather than a mistake.
 *   <li><strong>The schema.</strong> The library ships its own migrations and its own history table.
 * </ol>
 */
@Configuration
@ConfigurationProperties(prefix = "tessera.ai")
public class AiConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiConfiguration.class);

    /** What Spring Boot calls the bean that runs the product's own migrations. */
    private static final String PRODUCT_FLYWAY_INITIALIZER = "flywayInitializer";

    // ── The guards ───────────────────────────────────────────────────────────────

    /**
     * ⚠️ <strong>The ceiling and the confirmation threshold are not inherited thoughtlessly.</strong>
     * A tracker's bulk operation is a handful of issues, not a hundred rows, so the ceiling is lower
     * than Innoventa's and confirmation starts sooner. Nothing here writes in bulk yet; the numbers are
     * the ones that will be right when something does.
     */
    @Bean
    public GuardSettings aiGuardSettings() {
        return new GuardSettings(50, 3, Duration.ofMinutes(5), Duration.ofSeconds(60));
    }

    @Bean
    public ConfirmationStore aiConfirmationStore(
            EntityManagerFactory entityManagers, GuardSettings settings) {

        // The library's JPA store rather than memory: a preview and its confirmation are two requests
        // and nothing promises they reach the same instance. In memory it works perfectly until there
        // is a second replica, and then fails as "that confirmation is not valid" on half of them.
        return new JpaConfirmationStore(entityManagers, settings.confirmationLifetime());
    }

    @Bean
    public GuardChain aiGuardChain(GuardSettings settings, ConfirmationStore confirmations) {
        return new GuardChain(List.of(
                new RateLimitGuard(new TokenBucketCallerRateLimiter(60, Duration.ofMinutes(1))),
                new CeilingGuard(settings),
                new EmptyDestructionGuard(),
                new ConfirmationGuard(confirmations, settings),
                new DeduplicationGuard(new InMemoryDuplicateCallStore(settings.deduplicationWindow()))));
    }

    // ── The catalogue and the dispatcher ─────────────────────────────────────────

    /**
     * Every permission this build knows, so the catalogue refuses an action naming one that does not
     * exist. Read off {@link Permissions}' constants rather than listed again — a second list beside
     * them is one commit behind from the day it is written.
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

    @Bean
    public ToolCatalog aiToolCatalog(
            ObjectProvider<ToolDefinition> definitions,
            PermissionVocabulary           vocabulary,
            GuardChain                     guards) {

        return ToolCatalog.of(
                definitions.orderedStream().toList(),
                vocabulary,
                // Every guard this product believes it has, named — so a chain missing one refuses to
                // start rather than running without protection somebody thinks is there.
                new GuardRoster(
                        Set.of(RateLimitGuard.NAME, CeilingGuard.NAME, EmptyDestructionGuard.NAME,
                               ConfirmationGuard.NAME, DeduplicationGuard.NAME),
                        guards.names()));
    }

    @Bean
    public ToolDispatcher aiToolDispatcher(
            ToolCatalog     catalog,
            CallerResolver  callers,
            ToolAuthorizer  authorizer,
            ScopeResolver   scopes,
            GuardChain      guards) {

        // ⚠️ InvocationTrace.none() is a stated gap, not an oversight. Tessera already records every
        // issue change in its own activity log through the services the handlers call, so a tool call
        // is audited by the same rows a person's edit is. What is NOT recorded is the call itself —
        // how often each action is used and where it is refused — and that arrives with `jmouse-ai-jpa`'s
        // counter when there is somebody to read it.
        return new ToolDispatcher(catalog, callers, authorizer, scopes, guards, InvocationTrace.none());
    }

    // ── The model, if there is one ───────────────────────────────────────────────

    private final Provider provider = new Provider();

    public Provider getProvider() {
        return provider;
    }

    /**
     * The provider, if one is configured.
     *
     * <p>⚠️ Conditional on the name being set, so an installation with tools and no model starts
     * perfectly and simply has no assistant — {@code AssistantService} asks for the runner lazily and
     * says so.
     */
    @Bean
    @ConditionalOnProperty(name = "tessera.ai.provider.name")
    public ChatModel aiChatModel() {
        ProviderSettingsSource settings = ProviderSettingsSource.fixed(new ProviderSettings(
                normalised(provider.getName()),
                provider.getModel(),
                provider.getApiKey(),
                provider.getApiUrl(),
                provider.getMaximumTokens()));

        return switch (normalised(provider.getName())) {
            case AnthropicChatModel.PROVIDER_NAME -> new AnthropicChatModel(settings);
            case OpenAiChatModel.PROVIDER_NAME    -> new OpenAiChatModel(settings);
            case GatewayChatModel.PROVIDER_NAME   -> new GatewayChatModel(settings);
            default -> throw new IllegalStateException(
                    "'tessera.ai.provider.name' is '" + provider.getName() + "', which is not a "
                    + "provider this library ships. Available: anthropic, openai, gateway. Leave it "
                    + "unset for an installation that has tools and no model.");
        };
    }

    @Bean
    @ConditionalOnProperty(name = "tessera.ai.provider.name")
    public ConversationRunner aiConversationRunner(ChatModel model, ToolDispatcher dispatcher) {
        return new ConversationRunner(model, dispatcher, new ConversationBudget(6, 120_000));
    }

    // ── The schema ───────────────────────────────────────────────────────────────

    /**
     * The library's own tables, in the library's own history.
     *
     * <p>⚠️ <strong>A separate history table is the only thing that works.</strong> The library's
     * migrations start at {@code V000001} and so do Tessera's; sharing {@code flyway_schema_history}
     * would collide on the version and fail on the checksum.
     *
     * <p>⚠️ And see {@code spring.flyway.baseline-version} in {@code application.yml}: these running
     * first leave the schema non-empty, which is exactly the condition under which
     * {@code baseline-on-migrate} skips a product's own first migration.
     */
    @Bean(name = AiMigrations.MIGRATOR_BEAN_NAME)
    public InitializingBean aiFlywayMigrator(DataSource dataSource) {
        return () -> {
            AiDialect dialect  = AiDialect.resolve(dataSource);
            String    location = AiMigrations.locationFor(dialect);

            MigrateResult result = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(location)
                    .table(AiMigrations.HISTORY_TABLE)
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .validateOnMigrate(true)
                    .load()
                    .migrate();

            LOGGER.info("AI schema at {} ({}) — {} migration(s) applied, now at version {}",
                    location, AiMigrations.HISTORY_TABLE,
                    result.migrationsExecuted, result.targetSchemaVersion);
        };
    }

    /**
     * ⚠️ Ordering, and the failure without it is silent until the first query: Hibernate validates
     * against whatever tables exist when it builds. Hanging the AI migrator off {@code flywayInitializer}
     * puts it in the same place in the order without this file knowing where the entity manager is built.
     */
    @Bean
    public static BeanFactoryPostProcessor aiMigrationsRunFirst() {
        return beanFactory -> {
            if (!beanFactory.containsBeanDefinition(PRODUCT_FLYWAY_INITIALIZER)) {
                return;
            }

            BeanDefinition initializer = beanFactory.getBeanDefinition(PRODUCT_FLYWAY_INITIALIZER);
            List<String>   dependsOn   = new ArrayList<>();

            if (initializer.getDependsOn() != null) {
                dependsOn.addAll(Arrays.asList(initializer.getDependsOn()));
            }

            if (!dependsOn.contains(AiMigrations.MIGRATOR_BEAN_NAME)) {
                dependsOn.add(AiMigrations.MIGRATOR_BEAN_NAME);
                initializer.setDependsOn(dependsOn.toArray(String[]::new));
            }
        };
    }

    private static String normalised(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException unreadable) {
            throw new IllegalStateException("Unable to read permission constant " + field.getName(),
                    unreadable);
        }
    }

    /** Bound from {@code tessera.ai.provider.*}. */
    public static class Provider {

        private String name;
        private String model;
        private String apiKey;
        private String apiUrl;
        private int    maximumTokens = 4_096;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public int getMaximumTokens() {
            return maximumTokens;
        }

        public void setMaximumTokens(int maximumTokens) {
            this.maximumTokens = maximumTokens;
        }
    }
}
