package com.smartlibrary.repository;

import com.smartlibrary.entity.Membership;
import com.smartlibrary.entity.MembershipStatus;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository
        extends JpaRepository<Membership, Long> {

    // ==========================================
    // MEMBERSHIP HISTORY
    // ==========================================

    List<Membership> findByUser_IdOrderByEndDateDescIdDesc(
            Long userId
    );

    // ==========================================
    // FIND MEMBERSHIPS BY USER AND STATUS
    // ==========================================

    List<Membership> findByUser_IdAndStatus(
            Long userId,
            MembershipStatus status
    );

    // ==========================================
    // FIND ACTIVE MEMBERSHIP
    // ==========================================

    @Query("""
            select m from Membership m
            where m.user.id = :userId
              and m.status =
                  com.smartlibrary.entity.MembershipStatus.ACTIVE
              and m.startDate <= :today
              and m.endDate >= :today
            order by m.endDate desc
            """)
    List<Membership> findActiveOn(
            @Param("userId") Long userId,
            @Param("today") LocalDate today
    );

    // ==========================================
    // COUNT ACTIVE MEMBERS
    // ==========================================

    @Query("""
            select count(distinct m.user.id)
            from Membership m
            where m.status =
                  com.smartlibrary.entity.MembershipStatus.ACTIVE
              and m.startDate <= :today
              and m.endDate >= :today
            """)
    long countActiveMembers(
            @Param("today") LocalDate today
    );

    // ==========================================
    // AUTOMATIC MEMBERSHIP EXPIRY
    // ==========================================

    @Modifying
    @Query("""
            update Membership m
            set m.status =
                com.smartlibrary.entity.MembershipStatus.EXPIRED
            where m.status =
                  com.smartlibrary.entity.MembershipStatus.ACTIVE
              and m.endDate < :today
            """)
    int expireEndedBefore(
            @Param("today") LocalDate today
    );

    // ==========================================
    // FIND UPCOMING MEMBERSHIP EXPIRIES
    // ==========================================

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            select m from Membership m
            where m.status =
                  com.smartlibrary.entity.MembershipStatus.ACTIVE
              and m.startDate <= :today
              and m.endDate between :today and :endDate
            order by m.endDate asc
            """)
    List<Membership> findExpiringMemberships(
            @Param("today") LocalDate today,
            @Param("endDate") LocalDate endDate
    );

    // ==========================================
    // FIND RECENTLY EXPIRED MEMBERSHIPS
    // ==========================================

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            select m from Membership m
            where m.status in (
                com.smartlibrary.entity.MembershipStatus.ACTIVE,
                com.smartlibrary.entity.MembershipStatus.EXPIRED
            )
            and m.endDate = :expiredDate
            order by m.id desc
            """)
    List<Membership> findRecentlyExpiredMemberships(
            @Param("expiredDate") LocalDate expiredDate
    );

    // ==========================================
    // CHECK MEMBERSHIP RENEWAL
    // ==========================================

    @Query("""
            select count(m) from Membership m
            where m.user.id = :userId
              and m.id <> :membershipId
              and m.status =
                  com.smartlibrary.entity.MembershipStatus.ACTIVE
              and m.endDate > :previousEndDate
              and m.startDate <= :nextDay
            """)
    long countReplacementMemberships(
            @Param("userId") Long userId,
            @Param("membershipId") Long membershipId,
            @Param("previousEndDate") LocalDate previousEndDate,
            @Param("nextDay") LocalDate nextDay
    );
}