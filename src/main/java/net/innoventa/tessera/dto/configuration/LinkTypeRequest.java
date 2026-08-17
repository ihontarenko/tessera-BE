package net.innoventa.tessera.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import net.innoventa.tessera.domain.LinkTypeEffect;

/**
 * A kind of relationship between two issues.
 *
 * <p>Both labels are required, and a symmetric type says the same word twice rather than leaving one
 * blank — the direction is still stored, the labels simply match. A blank inward label would render as
 * an empty half of every link that used it.
 *
 * <p>⚠️ The {@code effect} is what the product <em>does</em> with such a link, and omitting it means
 * {@link LinkTypeEffect#NONE} — a relationship for a reader. It is not required, because "this one is
 * just informational" is the honest default for a new type somebody has only named so far.
 */
public record LinkTypeRequest(
    @NotBlank @Size(max = 64) String name,
    @NotBlank @Size(max = 64) String outwardLabel,
    @NotBlank @Size(max = 64) String inwardLabel,
    LinkTypeEffect effect
) {

    /** ⚠️ Absent means {@code NONE}, never null — see {@code LinkType.effect} for why null is not a value. */
    public LinkTypeEffect effectOrNone() {
        return effect == null ? LinkTypeEffect.NONE : effect;
    }

}
