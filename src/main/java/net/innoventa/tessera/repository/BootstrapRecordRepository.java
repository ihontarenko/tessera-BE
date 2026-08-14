package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.BootstrapRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BootstrapRecordRepository extends JpaRepository<BootstrapRecord, String> {

    Optional<BootstrapRecord> findByStepKey(String stepKey);

}
