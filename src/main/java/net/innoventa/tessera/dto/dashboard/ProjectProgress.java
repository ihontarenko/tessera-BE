package net.innoventa.tessera.dto.dashboard;

/**
 * One project as three numbers — the segments of its progress meter.
 *
 * <p>⚠️ <strong>Archived issues are left out.</strong> Only a resolved issue can be filed, so counting
 * them would put every project's whole finished history into the done segment: a year-old project
 * would read as 98% done for ever, and the meter would stop being able to say anything about the work
 * in front of anybody. This measures the project that is still live.
 */
public record ProjectProgress(String projectId, long todo, long inProgress, long done) {

    public long total() {
        return todo + inProgress + done;
    }
}
