package net.innoventa.tessera.domain;

/**
 * What can be filed into a {@link Category}.
 *
 * <p>⚠️ <strong>This enum is the whole cost of adding a second kind</strong>, and that is the point of
 * the {@code entity_categories} table being polymorphic rather than a {@code category_id} column on
 * {@link WikiPage}. A file cabinet, an attachment, a saved filter — each is a constant here and a
 * repository call, not a migration of anything that already exists.
 *
 * <p>Which is also why the column carries no {@code CHECK} constraint, against the house form of every
 * other enum-shaped column in this schema: a check listing today's values would make tomorrow's addition
 * exactly the migration this shape exists to avoid, and it would guard nothing — the column is written
 * only from this type.
 */
public enum CategoryEntityType {

    /** A wiki page (TSSR-16), and for now the only thing anybody files. */
    PAGE

}
