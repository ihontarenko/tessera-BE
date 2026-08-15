package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.MemberAvatarView;
import net.innoventa.tessera.exception.InvalidRequestException;
import net.innoventa.tessera.exception.ResourceNotFoundException;
import net.innoventa.tessera.repository.MemberRepository;
import org.jmouse.storage.Content;
import org.jmouse.storage.ContentTypes;
import org.jmouse.storage.delivery.DeliverableFile;
import org.jmouse.storage.delivery.DeliveryIntent;
import org.jmouse.storage.delivery.DeliveryPlan;
import org.jmouse.storage.delivery.DeliveryPlanner;
import org.jmouse.storage.jpa.StoredFile;
import org.jmouse.storage.jpa.StoredFileIngestion;
import org.jmouse.storage.jpa.StoredFileRegistry;
import org.jmouse.storage.key.StorageKeyRequest;
import org.jmouse.storage.spring.MultipartContent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A member's face: choosing one, uploading one, dropping back to initials, and serving the bytes.
 *
 * <p>Everything about the bytes is the library's — the acceptance policy, the key layout, the digest,
 * the deduplication, the registry row and the decision between streaming and a presigned redirect.
 * What lives here is the only part Tessera genuinely owns: which of the three kinds a member wears.
 *
 * <p>⚠️ <strong>Nothing is ever deleted.</strong> Replacing or clearing a picture unbinds the member
 * from an object and stops there. Keys are content-addressed, so two people who upload the same image
 * share one row, and deleting it because one of them moved on takes the other's face away too.
 * Reclaiming what nothing points at is the sweeper's job, and it asks
 * {@code StorageReferencesConfiguration} who is still pointing.
 */
@Service
@RequiredArgsConstructor
public class MemberAvatarService {

    /** Logical content class of avatars, and the prefix their keys sit under. */
    private static final String AVATAR_NAMESPACE = "avatars";

    /**
     * What an avatar may be.
     *
     * <p>⚠️ <strong>This narrowing lives here rather than in configuration because the library's
     * `CUSTOM` upload profile does not bind</strong> — see the comment on {@code tessera.file.upload}
     * in application.yml for both failure modes. The configured profile is an allowlist that already
     * excludes SVG and every other script host, which is what makes
     * {@code PublicAvatarController}'s unauthenticated route safe; what it does not do is stop somebody
     * making their face a PDF. This does.
     *
     * <p>Raster only, and no SVG here either even though the profile would already refuse it. Two
     * independent statements of the same rule is the point: whichever one somebody edits later, the
     * other still holds.
     */
    private static final Set<String> ACCEPTED_PICTURE_TYPES =
        Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    /**
     * What a preset seed may look like.
     *
     * <p>⚠️ A shape, not a catalog. The generator that draws these faces is total — every string
     * produces one — so there is no set of valid seeds for this to check against, and inventing one
     * here would mean a curated list on the server that has to be edited in step with the interface's
     * every time a face is added. What this refuses is a seed that could not have come from a picker:
     * something long enough to be a payload, or carrying characters a URL and a column should not have
     * to think about.
     */
    private static final Pattern SEED_SHAPE = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private static final int SEED_MAXIMUM_LENGTH = 64;

    private final MemberRepository     memberRepository;
    private final StoredFileIngestion  ingestion;
    private final StoredFileRegistry   registry;
    private final DeliveryPlanner      deliveryPlanner;

    /** Wear a generated pixel face, drawn from {@code seed}. */
    @Transactional
    public MemberAvatarView choosePreset(Member member, String seed) {
        member.wearsPreset(validSeed(seed));

        return MemberAvatarView.from(memberRepository.save(member));
    }

    /**
     * Wear an uploaded picture.
     *
     * <p>The member is not part of the storage key beyond namespacing, because content-addressed keys
     * mean the key is a digest: two members who upload the same picture reach the same object and the
     * second upload writes nothing at all.
     */
    @Transactional
    public MemberAvatarView uploadPicture(Member member, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("No picture was uploaded.");
        }

        Content content = MultipartContent.of(file);

        ensurePicture(content);

        StorageKeyRequest request = StorageKeyRequest.forContent(member.getId(), content)
            .namespace(AVATAR_NAMESPACE)
            .build();

        member.wearsPicture(ingestion.ingest(request, content));

        return MemberAvatarView.from(memberRepository.save(member));
    }

    /** Drop back to drawn initials. */
    @Transactional
    public MemberAvatarView clear(Member member) {
        member.wearsInitials();

        return MemberAvatarView.from(memberRepository.save(member));
    }

    /**
     * How the bytes of a stored avatar should reach the client.
     *
     * <p>Addressed by the stored object rather than by the member who wears it, which is what makes the
     * route cacheable forever and safe to serve unauthenticated — see {@code PublicAvatarController}.
     * It also means a picture somebody has since replaced keeps resolving, which is correct: a page
     * rendered a moment ago should not develop holes.
     */
    @Transactional(readOnly = true)
    public DeliveryPlan planDelivery(String storedFileId, DeliveryIntent intent) {
        StoredFile storedFile = registry.find(storedFileId)
            .orElseThrow(() -> new ResourceNotFoundException("No such avatar: " + storedFileId));

        DeliverableFile file = new DeliverableFile(
            storedFile.getStorageKey(), storedFile.getBackend(), storedFile.getOriginalName(),
            storedFile.getContentType(), storedFile.getSizeBytes(), storedFile.getSha256());

        return deliveryPlanner.plan(file, intent);
    }

    /**
     * Refuses anything that is not a raster image, before the bytes are written.
     *
     * <p>Judged on the declared type stripped of its parameters, which is what the library's own policy
     * compares too — {@code image/png; charset=binary} is a PNG.
     */
    private void ensurePicture(Content content) {
        String declared = ContentTypes.baseType(content.declaredContentType());

        if (declared == null || !ACCEPTED_PICTURE_TYPES.contains(declared.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException(
                "An avatar has to be a PNG, JPEG, WebP or GIF image — '%s' is not one."
                    .formatted(declared == null ? "unknown" : declared));
        }
    }

    private String validSeed(String seed) {
        String candidate = seed == null ? "" : seed.trim();

        if (candidate.isEmpty()) {
            throw new InvalidRequestException("A preset avatar needs a seed.");
        }

        if (candidate.length() > SEED_MAXIMUM_LENGTH || !SEED_SHAPE.matcher(candidate).matches()) {
            throw new InvalidRequestException(
                "'%s' is not a usable avatar seed — lowercase words joined by hyphens, up to %d characters."
                    .formatted(candidate, SEED_MAXIMUM_LENGTH));
        }

        return candidate;
    }

}
