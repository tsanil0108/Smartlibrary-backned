package com.smartlibrary.repository;

import com.smartlibrary.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository
        extends JpaRepository<Coupon, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.code = :code")
    Optional<Coupon> lock(@Param("code") String code);
}