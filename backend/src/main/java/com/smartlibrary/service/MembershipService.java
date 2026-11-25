package com.smartlibrary.service;

import com.smartlibrary.dto.MembershipDto.MembershipResponse;
import com.smartlibrary.dto.MembershipDto.UpdateMembershipRequest;
import com.smartlibrary.entity.Membership;
import com.smartlibrary.entity.MembershipStatus;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.NotFoundException;
import com.smartlibrary.repository.MembershipRepository;
import com.smartlibrary.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    // ==========================================
    // VALIDATE ACTIVE MEMBERSHIP
    // ==========================================

    @Transactional(readOnly = true)
    public Membership requireActiveMembership(
            Long userId,
            LocalDate today
    ) {

        List<Membership> active =
                membershipRepository.findActiveOn(userId, today);

        if (active.isEmpty()) {
            throw new BusinessRuleException(
                    "MEMBERSHIP_INACTIVE",
                    "Membership is missing, expired or suspended. "
                            + "Please renew your membership."
            );
        }

        return active.get(0);
    }

    // ==========================================
    // CURRENT MEMBERSHIP
    // ==========================================

    @Transactional(readOnly = true)
    public MembershipResponse currentFor(Long userId) {

        LocalDate today = LocalDate.now(clock);

        List<Membership> all =
                membershipRepository
                        .findByUser_IdOrderByEndDateDescIdDesc(userId);

        if (all.isEmpty()) {
            return MembershipResponse.none(userId);
        }

        Membership chosen = all.stream()
                .filter(m -> isValidOn(m, today))
                .findFirst()
                .orElse(all.get(0));

        return toResponse(chosen, today);
    }

    // ==========================================
    // MEMBERSHIP HISTORY
    // ==========================================

    @Transactional(readOnly = true)
    public List<MembershipResponse> history(Long userId) {

        ensureUserExists(userId);

        LocalDate today = LocalDate.now(clock);

        return membershipRepository
                .findByUser_IdOrderByEndDateDescIdDesc(userId)
                .stream()
                .map(m -> toResponse(m, today))
                .toList();
    }

    // ==========================================
    // ASSIGN / UPDATE MEMBERSHIP
    // ==========================================

    @Transactional
    public MembershipResponse assign(
            Long userId,
            UpdateMembershipRequest req,
            Long actorId
    ) {

        // Database locking prevents concurrent
        // membership updates for the same user.

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(
                        () -> new NotFoundException("User", userId)
                );

        if (user.getRole() != Role.MEMBER) {
            throw new BusinessRuleException(
                    "NOT_A_MEMBER",
                    "Memberships can only be assigned to members"
            );
        }

        if (req.startDate() == null || req.endDate() == null) {
            throw new BusinessRuleException(
                    "INVALID_DATES",
                    "Start date and end date are required"
            );
        }

        if (req.endDate().isBefore(req.startDate())) {
            throw new BusinessRuleException(
                    "INVALID_DATES",
                    "End date must not be before start date"
            );
        }

        if (req.planName() == null || req.planName().isBlank()) {
            throw new BusinessRuleException(
                    "INVALID_PLAN",
                    "Membership plan name is required"
            );
        }

        MembershipStatus status = req.status() == null
                ? MembershipStatus.ACTIVE
                : req.status();

        if (status != MembershipStatus.ACTIVE
                && status != MembershipStatus.SUSPENDED) {

            throw new BusinessRuleException(
                    "INVALID_STATUS",
                    "Status must be ACTIVE or SUSPENDED"
            );
        }

        // Cancel older active memberships.
        // Preserve old records for membership history.

        membershipRepository
                .findByUser_IdAndStatus(
                        userId,
                        MembershipStatus.ACTIVE
                )
                .forEach(old ->
                        old.setStatus(MembershipStatus.CANCELLED)
                );

        Membership membership = new Membership();

        membership.setUser(user);
        membership.setPlanName(req.planName().trim());
        membership.setStartDate(req.startDate());
        membership.setEndDate(req.endDate());
        membership.setStatus(status);

        membershipRepository.save(membership);

        auditService.log(
                actorId,
                "MEMBERSHIP_UPDATED",
                "MEMBERSHIP",
                membership.getId(),
                "user=" + userId
                        + " plan=" + membership.getPlanName()
                        + " " + membership.getStartDate()
                        + ".." + membership.getEndDate()
                        + " status=" + status
        );

        return toResponse(
                membership,
                LocalDate.now(clock)
        );
    }

    // ==========================================
    // INITIAL TRIAL MEMBERSHIP
    // ==========================================

    @Transactional
    public Membership createInitial(
            User user,
            String plan,
            int days
    ) {

        LocalDate today = LocalDate.now(clock);

        Membership membership = new Membership();

        membership.setUser(user);
        membership.setPlanName(plan);
        membership.setStartDate(today);
        membership.setEndDate(today.plusDays(days));
        membership.setStatus(MembershipStatus.ACTIVE);

        return membershipRepository.save(membership);
    }

    // ==========================================
    // AUTOMATIC MEMBERSHIP EXPIRY
    // ==========================================

    @Transactional
    public int expireEndedMemberships() {

        int count = membershipRepository.expireEndedBefore(
                LocalDate.now(clock)
        );

        if (count > 0) {
            log.info(
                    "Marked {} memberships as EXPIRED",
                    count
            );
        }

        return count;
    }

    // ==========================================
    // PRIVATE HELPER METHODS
    // ==========================================

    private void ensureUserExists(Long userId) {

        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User", userId);
        }
    }

    private boolean isValidOn(
            Membership membership,
            LocalDate day
    ) {

        return membership.getStatus() == MembershipStatus.ACTIVE
                && !day.isBefore(membership.getStartDate())
                && !day.isAfter(membership.getEndDate());
    }

    private MembershipResponse toResponse(
            Membership membership,
            LocalDate today
    ) {

        boolean valid = isValidOn(membership, today);

        String status =
                membership.getStatus() == MembershipStatus.ACTIVE
                        && membership.getEndDate().isBefore(today)
                        ? MembershipStatus.EXPIRED.name()
                        : membership.getStatus().name();

        long remaining = valid
                ? ChronoUnit.DAYS.between(
                today,
                membership.getEndDate()
        )
                : 0;

        return new MembershipResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getPlanName(),
                membership.getStartDate(),
                membership.getEndDate(),
                status,
                valid,
                remaining
        );
    }
}