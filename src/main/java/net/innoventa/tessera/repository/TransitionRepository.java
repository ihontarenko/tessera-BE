package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Transition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransitionRepository extends JpaRepository<Transition, String> {

    List<Transition> findByWorkflowId(String workflowId);

    List<Transition> findByWorkflowIdIn(List<String> workflowIds);

    boolean existsByWorkflowIdAndFromStatusIdAndToStatusId(String workflowId, String fromStatusId, String toStatusId);

    /** The create transition — the one with no {@code from} — naming the status a new issue lands in. */
    Optional<Transition> findFirstByWorkflowIdAndFromStatusIdIsNull(String workflowId);

    /** Every legal target from a given status in a workflow — the "available transitions" list. */
    List<Transition> findByWorkflowIdAndFromStatusId(String workflowId, String fromStatusId);

    /**
     * Every edge naming this status at either end — what refuses its deletion, and what a workflow
     * would lose with it. A status belongs to a workflow only through its transitions (there is no
     * {@code workflow_statuses} table), so this is also how "which workflows use it" is answered.
     */
    List<Transition> findByFromStatusIdOrToStatusId(String fromStatusId, String toStatusId);

    long countByWorkflowId(String workflowId);

    /** Deleting a workflow takes its edges with it — nothing else references a transition. */
    void deleteByWorkflowId(String workflowId);

}
