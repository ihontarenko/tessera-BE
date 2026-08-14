package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.InstanceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The instance settings row.
 *
 * <p>No finders: there is one row, its identifier is a constant, and V000015 seeds it. Anything reading
 * this goes through {@code InstanceDefaults}, which is where the absent-row case is turned into a
 * sentence rather than an {@code Optional} every caller has to invent an answer for.
 */
public interface InstanceSettingsRepository extends JpaRepository<InstanceSettings, String> {
}
