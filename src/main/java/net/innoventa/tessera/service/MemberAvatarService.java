package net.innoventa.tessera.service;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.domain.Member;
import net.innoventa.tessera.dto.MemberAvatarView;
import net.innoventa.tessera.exception.InvalidRequestException;
import net.innoventa.tessera.repository.MemberRepository;
import org.jmouse.avatar.AvatarService;
import org.jmouse.storage.spring.MultipartContent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * A member's face — what is left of it once the rules moved to the library.
 *
 * <p>This class was 178 lines: the accepted picture types, the seed shape, the size ceiling, the storage
 * namespace, the key layout, the delivery plan. ⚠️ <strong>Kiwi had the same 178 lines</strong>, differing
 * only in a size check it had added later. All of it is now {@code jmouse-avatars}, and what stays here is
 * the two things that genuinely are Tessera's: <strong>saving the row</strong> and <strong>answering with
 * the shape Tessera's screens read</strong>.
 *
 * <p>⚠️ The library mutates the member and persists nothing — deliberately, because a library that owned a
 * people table would make adopting it a schema negotiation. So the {@code save} below is not boilerplate
 * left over from the old version; it is the seam.</p>
 */
@Service
@RequiredArgsConstructor
public class MemberAvatarService {

    private final MemberRepository memberRepository;
    private final AvatarService    avatars;

    /** Wear a generated pixel face, drawn from {@code seed}. */
    @Transactional
    public MemberAvatarView choosePreset(Member member, String seed) {
        avatars.choosePreset(member, seed);

        return MemberAvatarView.from(memberRepository.save(member));
    }

    /** Wear an uploaded picture. */
    @Transactional
    public MemberAvatarView uploadPicture(Member member, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("No picture was uploaded.");
        }

        avatars.uploadPicture(member, MultipartContent.of(file));

        return MemberAvatarView.from(memberRepository.save(member));
    }

    /** Drop back to drawn initials. */
    @Transactional
    public MemberAvatarView clear(Member member) {
        avatars.clear(member);

        return MemberAvatarView.from(memberRepository.save(member));
    }
}
