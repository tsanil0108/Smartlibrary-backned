package com.smartlibrary.service;

import com.smartlibrary.dto.LibraryCardDto.CardResponse;
import com.smartlibrary.dto.LibraryCardDto.VerificationResponse;
import com.smartlibrary.dto.MembershipDto.MembershipResponse;

import com.smartlibrary.entity.LibraryCard;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.UserStatus;

import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.NotFoundException;

import com.smartlibrary.repository.LibraryCardRepository;
import com.smartlibrary.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryCardService {

    private final LibraryCardRepository cardRepository;

    private final UserRepository userRepository;

    private final MembershipService membershipService;

    private final QrCodeService qrCodeService;

    private final AuditService auditService;

    private final Clock clock;

    private static final String QR_PREFIX = "SLM:";

    private static final SecureRandom RANDOM =
            new SecureRandom();

    // ==========================================
    // GET OR CREATE MEMBER CARD
    // ==========================================

    @Transactional
    public CardResponse getMyCard(Long userId) {

        User user = lockMember(userId);

        LibraryCard card = cardRepository
                .findByUser_Id(userId)
                .orElseGet(
                        () -> createCard(user)
                );

        return toResponse(card);
    }

    // ==========================================
    // CREATE NEW LIBRARY CARD
    // ==========================================

    private LibraryCard createCard(User user) {

        LocalDateTime now =
                LocalDateTime.now(clock);

        LibraryCard card = new LibraryCard();

        card.setUser(user);

        card.setCardNumber(
                generateCardNumber(user.getId())
        );

        card.setVerificationToken(
                generateToken()
        );

        card.setActive(true);

        card.setCreatedAt(now);

        card.setUpdatedAt(now);

        LibraryCard saved =
                cardRepository.save(card);

        auditService.log(

                user.getId(),

                "LIBRARY_CARD_CREATED",

                "LIBRARY_CARD",

                saved.getId(),

                "Digital library card generated"
        );

        return saved;
    }

    // ==========================================
    // GENERATE UNIQUE CARD NUMBER
    // ==========================================

    private String generateCardNumber(Long userId) {

        return String.format(
                Locale.ROOT,
                "SLM-%06d",
                userId
        );
    }

    // ==========================================
    // GENERATE SECURE VERIFICATION TOKEN
    // ==========================================

    private String generateToken() {

        byte[] bytes = new byte[32];

        RANDOM.nextBytes(bytes);

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    // ==========================================
    // REGENERATE MEMBER QR CODE
    // ==========================================

    @Transactional
    public CardResponse regenerateQr(Long userId) {

        User user = lockMember(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {

            throw new BusinessRuleException(

                    "ACCOUNT_INACTIVE",

                    "Your account is not active"
            );
        }

        LibraryCard card = cardRepository
                .findByUser_Id(userId)
                .orElseGet(
                        () -> createCard(user)
                );

        if (!card.isActive()) {

            throw new BusinessRuleException(

                    "CARD_DISABLED",

                    "Your library card is disabled"
            );
        }

        // Old QR token becomes invalid

        card.setVerificationToken(
                generateToken()
        );

        card.setUpdatedAt(
                LocalDateTime.now(clock)
        );

        cardRepository.save(card);

        auditService.log(

                userId,

                "LIBRARY_CARD_QR_REGENERATED",

                "LIBRARY_CARD",

                card.getId(),

                "Library card QR regenerated"
        );

        return toResponse(card);
    }

    // ==========================================
    // ADMIN: VERIFY QR CODE
    // ==========================================

    @Transactional(readOnly = true)
    public VerificationResponse verifyQr(
            String qrContent
    ) {

        if (qrContent == null
                || !qrContent.startsWith(QR_PREFIX)) {

            return invalidCard(
                    "Invalid library QR code"
            );
        }

        String token = qrContent.substring(
                QR_PREFIX.length()
        );

        LibraryCard card = cardRepository
                .findByVerificationToken(token)
                .orElse(null);

        if (card == null) {

            return invalidCard(
                    "Invalid or expired QR code"
            );
        }

        return verificationResponse(card);
    }

    // ==========================================
    // ADMIN: VERIFY CARD NUMBER
    // ==========================================

    @Transactional(readOnly = true)
    public VerificationResponse verifyCardNumber(
            String cardNumber
    ) {

        LibraryCard card = cardRepository
                .findByCardNumber(
                        cardNumber.trim()
                                .toUpperCase(Locale.ROOT)
                )
                .orElse(null);

        if (card == null) {

            return invalidCard(
                    "Library card not found"
            );
        }

        return verificationResponse(card);
    }

    // ==========================================
    // ADMIN: ENABLE OR DISABLE CARD
    // ==========================================

    @Transactional
    public VerificationResponse updateCardStatus(

            Long userId,

            Long adminId,

            boolean active

    ) {

        lockMember(userId);

        LibraryCard card = cardRepository
                .findByUser_Id(userId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Library card for user",
                                userId
                        )
                );

        card.setActive(active);

        card.setUpdatedAt(
                LocalDateTime.now(clock)
        );

        cardRepository.save(card);

        auditService.log(

                adminId,

                active
                        ? "LIBRARY_CARD_ENABLED"
                        : "LIBRARY_CARD_DISABLED",

                "LIBRARY_CARD",

                card.getId(),

                "Card status updated"
        );

        return verificationResponse(card);
    }

    // ==========================================
    // VALIDATE MEMBER
    // ==========================================

    private User lockMember(Long userId) {

        User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "User",
                                userId
                        )
                );

        if (user.getRole() != Role.MEMBER) {

            throw new BusinessRuleException(

                    "NOT_A_MEMBER",

                    "Only members can have library cards"
            );
        }

        return user;
    }

    // ==========================================
    // GET CURRENT MEMBERSHIP
    // ==========================================

    private MembershipResponse getMembership(
            Long userId
    ) {

        return membershipService.currentFor(userId);
    }

    // ==========================================
    // DETERMINE CARD STATUS
    // ==========================================

    private String resolveStatus(

            LibraryCard card,

            MembershipResponse membership

    ) {

        if (!card.isActive()) {

            return "DISABLED";
        }

        if (card.getUser().getRole() != Role.MEMBER) {

            return "INVALID_MEMBER";
        }

        if (card.getUser().getStatus()
                != UserStatus.ACTIVE) {

            return "SUSPENDED";
        }

        if (membership == null) {

            return "NO_MEMBERSHIP";
        }

        if (!membership.valid()) {

            return "MEMBERSHIP_INACTIVE";
        }

        return "ACTIVE";
    }

    // ==========================================
    // MEMBER CARD RESPONSE
    // ==========================================

    private CardResponse toResponse(
            LibraryCard card
    ) {

        User user = card.getUser();

        MembershipResponse membership =
                getMembership(user.getId());

        String status = resolveStatus(
                card,
                membership
        );

        String qrCode = qrCodeService.generateQrCode(

                QR_PREFIX
                        + card.getVerificationToken()
        );

        return new CardResponse(

                card.getId(),

                user.getId(),

                card.getCardNumber(),

                user.getName(),

                user.getEmail(),

                membership != null
                        ? membership.planName()
                        : "No Membership",

                membership != null
                        ? membership.startDate()
                        : null,

                membership != null
                        ? membership.endDate()
                        : null,

                status,

                "ACTIVE".equals(status),

                qrCode,

                card.getCreatedAt()
        );
    }

    // ==========================================
    // ADMIN VERIFICATION RESPONSE
    // ==========================================

    private VerificationResponse verificationResponse(
            LibraryCard card
    ) {

        User user = card.getUser();

        MembershipResponse membership =
                getMembership(user.getId());

        String status = resolveStatus(
                card,
                membership
        );

        boolean valid = "ACTIVE".equals(status);

        return new VerificationResponse(

                valid,

                valid
                        ? "Library card verified successfully"
                        : "Library card is not currently valid",

                card.getCardNumber(),

                user.getId(),

                user.getName(),

                membership != null
                        ? membership.planName()
                        : "No Membership",

                membership != null
                        ? membership.endDate()
                        : null,

                status
        );
    }

    // ==========================================
    // INVALID CARD RESPONSE
    // ==========================================

    private VerificationResponse invalidCard(
            String message
    ) {

        return new VerificationResponse(

                false,

                message,

                null,

                null,

                null,

                null,

                null,

                "INVALID"
        );
    }
}