package com.smartlibrary.dto;

import com.smartlibrary.entity.MembershipStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class MembershipDto {
    private MembershipDto() {}


    public record UpdateMembershipRequest(
            @NotBlank @Size(max = 80) String planName,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            MembershipStatus status) {}   // optional: ACTIVE (default) or SUSPENDED

    public record MembershipResponse(Long id, Long userId, String planName, LocalDate startDate, LocalDate endDate,
                                     String status, boolean valid, long daysRemaining) {
        public static MembershipResponse none(Long userId) {
            return new MembershipResponse(null, userId, null, null, null, "NONE", false, 0);
        }
    }
}
