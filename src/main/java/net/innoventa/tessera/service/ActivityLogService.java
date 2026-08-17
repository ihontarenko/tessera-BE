package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.ActivityLog;
import net.innoventa.tessera.security.CallingAgent;
import org.jmouse.ai.agent.Agent;
import net.innoventa.tessera.domain.ActivityLogItem;
import net.innoventa.tessera.repository.ActivityLogItemRepository;
import net.innoventa.tessera.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The single recording seam for issue change history (ADR-0007), reused by every mutating issue
 * service. {@link #record(String, String, ChangeSet)} writes <strong>one</strong> {@link ActivityLog}
 * event with one {@link ActivityLogItem} per field that actually changed — so a single multi-field
 * edit reads as one grouped event, not a flurry of rows. Values are the point-in-time display strings
 * the caller passes; a later rename never rewrites them.
 * <p>
 * Recording is a discipline the framework does not enforce (ADR-0007): each mutation is responsible
 * for building its {@link ChangeSet} and calling this. An empty change set is a no-op.
 */
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    /** The longest snapshot a history item holds — {@code activity_log_items.old_value} / {@code new_value}. */
    private static final int MAXIMUM_SNAPSHOT_LENGTH = 1024;

    private static final String TRUNCATION_MARK = "…";

    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogItemRepository activityLogItemRepository;
    private final Supplier<String> idGenerator;
    private final CallingAgent callingAgent;

    /**
     * ⚠️ The agent is stamped here rather than passed in by each caller, and that is why every one of the
     * dozen services recording an event needed no change at all. It is a fact about the request, read
     * where the request's identity already lives — see {@link CallingAgent} for why that is safe.
     */
    @Transactional
    public void record(String issueId, String actorMemberId, ChangeSet changeSet) {
        if (changeSet.isEmpty()) {
            return;
        }

        Agent agent = callingAgent.current().orElse(null);

        ActivityLog event = activityLogRepository.save(ActivityLog.builder()
            .id(idGenerator.get())
            .issueId(issueId)
            .actorMemberId(actorMemberId)
            .agentId(agent == null ? null : agent.id())
            .agentName(agent == null ? null : agent.name())
            .build());

        for (FieldChange change : changeSet.changes()) {
            activityLogItemRepository.save(ActivityLogItem.builder()
                .id(idGenerator.get())
                .activityLogId(event.getId())
                .field(change.field())
                .oldValue(snapshot(change.oldValue()))
                .newValue(snapshot(change.newValue()))
                .build());
        }
    }

    /**
     * A value too long for a history item, shortened rather than allowed to fail the write.
     *
     * <p>⚠️ <strong>This is what stops a long field making its own issue uneditable</strong> (TSSR-1).
     * A history value is a display string; the edit is somebody's actual work. Letting the first refuse
     * the second is the wrong way round — and it failed in the most confusing way available, because
     * the column that overflowed was not the column being written: an update to {@code description}
     * came back as {@code Data too long for column 'new_value'}, with the description unchanged.
     *
     * <p>Truncating costs a reader an ellipsis in the History tab. Throwing cost them the edit. And it
     * is done here, at the one recording seam, so it holds for every field rather than for the one that
     * happened to be long first.
     */
    private static String snapshot(String value) {
        if (value == null || value.length() <= MAXIMUM_SNAPSHOT_LENGTH) {
            return value;
        }

        return value.substring(0, MAXIMUM_SNAPSHOT_LENGTH - TRUNCATION_MARK.length()) + TRUNCATION_MARK;
    }

    /** Remove an issue's entire history — its events and their items — when the issue is deleted. */
    @Transactional
    public void deleteForIssue(String issueId) {
        List<ActivityLog> events = activityLogRepository.findByIssueIdOrderByCreatedAtDesc(issueId);
        if (events.isEmpty()) {
            return;
        }

        List<String> eventIds = events.stream().map(ActivityLog::getId).toList();
        activityLogItemRepository.deleteAll(activityLogItemRepository.findByActivityLogIdIn(eventIds));
        activityLogRepository.deleteAll(events);
    }

    /** Start accumulating the field changes for one event. */
    public ChangeSet changeSet() {
        return new ChangeSet();
    }

    /** One field's before/after display-string snapshot. */
    public record FieldChange(String field, String oldValue, String newValue) {
    }

    /**
     * A fluent accumulator for one event's changes. {@link #compare} only records a field when the two
     * snapshots genuinely differ, so callers can throw every potentially-changed field at it and get a
     * clean event containing only the real changes.
     */
    public static final class ChangeSet {

        private final List<FieldChange> changes = new ArrayList<>();

        public ChangeSet compare(String field, String oldValue, String newValue) {
            if (!Objects.equals(oldValue, newValue)) {
                changes.add(new FieldChange(field, oldValue, newValue));
            }
            return this;
        }

        /**
         * Record a change unconditionally, for a field whose display strings may coincide across
         * genuinely different values — two sprints sharing a name, say. {@link #compare} would silently
         * swallow that; the caller here already knows the change is real.
         */
        public ChangeSet changed(String field, String oldValue, String newValue) {
            changes.add(new FieldChange(field, oldValue, newValue));
            return this;
        }

        /** Record an addition (no prior value) — for set-membership changes like adding a link. */
        public ChangeSet added(String field, String newValue) {
            changes.add(new FieldChange(field, null, newValue));
            return this;
        }

        /** Record a removal (no resulting value) — for set-membership changes like removing a link. */
        public ChangeSet removed(String field, String oldValue) {
            changes.add(new FieldChange(field, oldValue, null));
            return this;
        }

        public boolean isEmpty() {
            return changes.isEmpty();
        }

        public List<FieldChange> changes() {
            return List.copyOf(changes);
        }

    }

}
