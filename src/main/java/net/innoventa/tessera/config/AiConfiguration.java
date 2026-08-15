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
import org.jmouse.ai.administration.ProviderAdministration;
import org.jmouse.ai.agent.AgentConnections;
import org.jmouse.ai.agent.AgentDirectory;
import org.jmouse.ai.jpa.JpaAgentConnections;
import org.jmouse.ai.jpa.JpaAgentDirectory;
import org.jmouse.ai.jpa.JpaCallCounter;
import org.jmouse.ai.jpa.JpaConfirmationStore;
import org.jmouse.ai.jpa.JpaProviderAdministration;
import org.jmouse.ai.jpa.JpaProviderSettingsSource;
import org.jmouse.ai.jpa.migration.AiDialect;
import org.jmouse.ai.jpa.migration.AiMigrations;
import org.jmouse.ai.management.OverviewController;
import org.jmouse.ai.management.ProviderAdministrationController;
import org.jmouse.ai.management.ProviderController;
import org.jmouse.ai.management.ToolCallHistoryController;
import org.jmouse.ai.management.ToolCatalogController;
import org.jmouse.ai.management.UsageController;
import org.jmouse.ai.provider.ChatModel;
import org.jmouse.ai.provider.ProviderSettingsSource;
import org.jmouse.ai.provider.RoutingChatModel;
import org.jmouse.ai.spi.CallerResolver;
import org.jmouse.ai.spi.ConfirmationStore;
import org.jmouse.ai.spi.PermissionVocabulary;
import org.jmouse.ai.spi.ScopeResolver;
import org.jmouse.ai.spi.ToolAuthorizer;
import org.jmouse.ai.conversation.ConversationBudget;
import org.jmouse.ai.conversation.ConversationRunner;
import org.jmouse.ai.conversation.SettingsProviderRegistry;
import org.jmouse.ai.view.ProviderRegistry;
import org.jmouse.ai.view.ToolCallHistory;
import org.jmouse.ai.view.ToolCatalogView;
import org.jmouse.ai.view.UsageTotals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
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
import java.util.Map;
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
 *   <li><strong>A model</strong>, resolved per turn from the row that is in force. An installation
 *       with no active row has tools and no assistant, which is a supported arrangement rather than a
 *       mistake — a connected protocol client reaches every action without a model.
 *   <li><strong>The read ports a management screen needs</strong> — the catalogue, the counters and
 *       what is in force.
 *   <li><strong>The schema.</strong> The library ships its own migrations and its own history table.
 * </ol>
 *
 * <p>⚠️ <strong>The provider stopped being a property.</strong> It used to be
 * {@code tessera.ai.provider.*} bound here and frozen at startup; it is now a row in
 * {@code ai_provider_settings}, administered on the AI screen behind {@code ai:administer}. That is
 * Innoventa's arrangement and the one {@code application.yml} said to copy — rotating a key is a form
 * rather than a file, a deploy and an environment variable somebody has to be trusted with.
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
            GuardChain      guards,
            JpaCallCounter  counter) {

        // The counter, now that there is a screen to read it on. Tessera still records every issue
        // change in its own activity log through the services the handlers call, so WHAT a tool did is
        // audited by the same rows a person's edit is; what that never recorded is the CALL — how often
        // each action is used and where it is refused — and this is that.
        return new ToolDispatcher(catalog, callers, authorizer, scopes, guards, counter);
    }

    // ── What a management screen reads ───────────────────────────────────────────

    /**
     * The counters, which are both halves at once.
     *
     * <p>{@link JpaCallCounter} implements the writing port ({@code InvocationTrace}) and the reading
     * one ({@link UsageTotals}) over the same rows. Its table arrives with the library's own migrations.
     *
     * <p>⚠️ <strong>One bean, injected by its own type.</strong> Declaring a second one that returned
     * the same instance as {@link UsageTotals} looks tidier and refuses to start: two candidates for one
     * port, and the message says nothing about counters.
     */
    @Bean
    public JpaCallCounter aiCallCounter(EntityManagerFactory entityManagers) {
        return new JpaCallCounter(entityManagers);
    }

    // ── Agents and the clients connected to them ─────────────────────────────────

    /**
     * Which agents exist, who owns them, and whether they may act.
     *
     * <p>⚠️ <strong>These two replaced a {@code mcp_credentials} table this product owned.</strong> The
     * old row was the <em>connection</em> and nothing else, which meant there was no identity to grant a
     * narrower permission to, nothing to switch off short of ending every client, and nothing for a
     * record to name afterwards. An {@code Agent} is the persona; a connection belongs to one; one agent
     * has many.
     *
     * <p>Wired by hand because this product wires the whole library by hand — it takes the modules rather
     * than {@code jmouse-ai-spring-boot}. The tables arrive through
     * {@link #aiFlywayMigrator(DataSource)} below, in the library's own history.
     */
    @Bean
    public AgentDirectory aiAgentDirectory(EntityManagerFactory entityManagers) {
        return new JpaAgentDirectory(entityManagers);
    }

    /**
     * The clients connected to those agents.
     *
     * <p>⚠️ Storage only. What a credential <em>is</em> stays with {@code McpCredentialService}: HS256,
     * a secret only this product holds, and a confinement that is a signature rather than a check.
     */
    @Bean
    public AgentConnections aiAgentConnections(EntityManagerFactory entityManagers) {
        return new JpaAgentConnections(entityManagers);
    }

    /**
     * ⚠️ <strong>Empty, and the screen says which kind of empty it is.</strong> A per-call trail is a
     * product's rows in a product's vocabulary — Innoventa answers this port from its audit log — and
     * Tessera has no such table. The counters above are what it has instead, so the Activity screen
     * shows totals and states outright that no per-call trail is recorded, rather than showing an empty
     * list that reads as "nothing has ever been called".
     */
    @Bean
    public ToolCallHistory aiToolCallHistory() {
        return ToolCallHistory.none();
    }

    /** The catalogue's answers, handed over without handing over the catalogue itself. */
    @Bean
    public ToolCatalogView aiToolCatalogView(ToolCatalog catalog) {
        return ToolCatalogView.over(catalog);
    }

    /**
     * What is in force, with the credential reduced to a yes or a no at the boundary.
     *
     * <p>⚠️ Answered from the settings source rather than from the rows, deliberately: the screen's
     * whole job is to tell "what somebody typed" apart from "what resolved", and those disagree when
     * nothing is active, when two rows are, and when the active row has no key.
     */
    @Bean
    public ProviderRegistry aiProviderRegistry(ProviderSettingsSource settingsSource) {
        return new SettingsProviderRegistry(settingsSource);
    }

    // ── The model, which is a row rather than a property ─────────────────────────

    /**
     * Whose rows these are, where one installation's {@code ai_provider_settings} serves more than one
     * application. Bound from {@code tessera.ai.application}.
     */
    private String application = "tessera";

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    /**
     * The settings in force, read from the database on every call.
     *
     * <p><strong>This is what makes the provider a screen and not a redeploy.</strong> Which provider
     * answers, which model, what one answer may cost and the key it authenticates with are all rows in
     * {@code ai_provider_settings}, administered by somebody holding {@code ai:administer} — rather
     * than a file, a deploy and an environment variable somebody has to be trusted with. Rotating a
     * leaked key is a form and takes effect on the next request.
     *
     * <p>⚠️ Reading rather than caching: settings bound into a bean at startup mean a restart, which is
     * exactly what nobody wants to be doing at the moment a key leaks. One indexed query against one
     * row, next to an HTTP call that will take a thousand times longer.
     */
    @Bean
    public ProviderSettingsSource aiProviderSettingsSource(EntityManagerFactory entityManagers) {
        return new JpaProviderSettingsSource(entityManagers, application);
    }

    /**
     * The write half of the same rows.
     *
     * <p>⚠️ <strong>The library's, not Tessera's.</strong> Every rule it keeps — exactly one row in
     * force, a blank key meaning <em>keep the stored one</em>, a refusal to delete what is in force — is
     * a condition {@link JpaProviderSettingsSource} above refuses to resolve without. Re-deriving them
     * in a product service was the duplicate this cluster removed, in both directions: the rules and the
     * six routes over them now exist once.
     */
    @Bean
    public ProviderAdministration aiProviderAdministration(EntityManagerFactory entityManagers) {
        return new JpaProviderAdministration(entityManagers, application);
    }

    /**
     * The model, chosen by the row that is in force.
     *
     * <p>⚠️ Unconditional, unlike the property-driven bean it replaces. An installation with no active
     * row still starts perfectly and simply has no assistant — the refusal comes from the settings
     * source at the moment somebody asks, and {@code AssistantService} asks
     * {@link org.jmouse.ai.view.ProviderRegistry} rather than this bean whether there is anything to
     * ask.
     */
    @Bean
    public ChatModel aiChatModel(ProviderSettingsSource settingsSource) {
        return RoutingChatModel.overShippedProviders(settingsSource);
    }

    @Bean
    public ConversationRunner aiConversationRunner(ChatModel model, ToolDispatcher dispatcher) {
        return new ConversationRunner(model, dispatcher, new ConversationBudget(6, 120_000));
    }

    // ── The screens, which are the library's controllers ─────────────────────────

    /**
     * {@code jmouse-ai-management}'s six controllers, declared by hand.
     *
     * <p><strong>The module rather than a copy of it.</strong> Innoventa wrote its own
     * {@code /api/ai} controller because a library's handler cannot carry {@code @RequiresAccess} and
     * guarding it meant a URL rule gated on a <em>role</em>. That was a real problem with the wrong fix:
     * the answer is to declare the requirement <em>about</em> a foreign type — see {@code AiManagementAccess}
     * — after which these are gated by the same engine, on the same axes, as everything Tessera wrote.
     *
     * <p>Declared here rather than auto-configured because there is no starter on Boot 3: {@code
     * AiManagementAutoConfiguration} does exactly this, and this is that file's hand-written half.
     *
     * <p>Where they answer is {@code jmouse.ai.management.prefix}, set to {@code /api/ai} — inside the
     * prefix Spring Security already authenticates, and where the interface's own client is pointed.
     */
    @Bean
    public ToolCatalogController aiToolCatalogController(ToolCatalogView tools) {
        return new ToolCatalogController(tools);
    }

    @Bean
    public ToolCallHistoryController aiToolCallHistoryController(
            ToolCallHistory history, ToolCatalogView tools) {

        return new ToolCallHistoryController(history, tools);
    }

    @Bean
    public UsageController aiUsageController(JpaCallCounter counter) {
        return new UsageController(counter);
    }

    @Bean
    public ProviderController aiProviderController(ProviderRegistry providers) {
        return new ProviderController(providers);
    }

    @Bean
    public OverviewController aiOverviewController(
            ProviderRegistry providers, ToolCatalogView tools, ToolCallHistory history) {

        return new OverviewController(providers, tools, history);
    }

    @Bean
    public ProviderAdministrationController aiProviderAdministrationController(
            ProviderAdministration configurations) {

        return new ProviderAdministrationController(configurations);
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

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException unreadable) {
            throw new IllegalStateException("Unable to read permission constant " + field.getName(),
                    unreadable);
        }
    }

}
