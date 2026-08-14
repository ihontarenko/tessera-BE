package net.innoventa.tessera.repository;

import net.innoventa.tessera.domain.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriorityRepository extends JpaRepository<Priority, String> {

    List<Priority> findAllByOrderBySequenceAsc();

    /**
     * ⚠️ <strong>Case-insensitively, which is stricter than the database.</strong> The unique constraint
     * is on the exact string, so "Bug" and "bug" would both be accepted and then be two catalog rows
     * nobody can tell apart in a picker. A refusal an administrator can read beats a constraint that
     * lets the confusing case through.
     */
    boolean existsByNameIgnoreCase(String name);

    /** The same question while renaming, where the row's own name is not a collision with itself. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);

    /** The highest position in the picker, so a newly created priority is appended rather than inserted. */
    Optional<Priority> findFirstByOrderBySequenceDesc();

}
