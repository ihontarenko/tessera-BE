package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.ActivityLogItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogItemRepository extends JpaRepository<ActivityLogItem, String> {

    List<ActivityLogItem> findByActivityLogIdIn(List<String> activityLogIds);

}
