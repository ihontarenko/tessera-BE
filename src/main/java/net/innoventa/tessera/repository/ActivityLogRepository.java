package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, String> {

    List<ActivityLog> findByIssueIdOrderByCreatedAtDesc(String issueId);

}
