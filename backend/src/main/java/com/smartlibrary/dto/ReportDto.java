package com.smartlibrary.dto;

import com.smartlibrary.entity.AuditLog;
import java.time.Instant;
import java.util.Map;

public final class ReportDto {
    private ReportDto() {}

    public record DashboardResponse(long totalTitles, long totalCopies, long availableCopies, long totalMembers,
                                    long activeMembers, long activeLoans, long overdueLoans) {}

    public record CategoryStat(String category, long loans, double percentage) {}

    public record MonthStat(int year, int month, long loans) {}

    public record InventoryStatus(long totalCopies, Map<String, Long> byStatus) {}

    public record AuditLogResponse(Long id, Long actorUserId, String action, String entityType, Long entityId,
                                   String details, Instant createdAt) {
        public static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getActorUserId(), a.getAction(), a.getEntityType(),
                    a.getEntityId(), a.getDetails(), a.getCreatedAt());
        }
    }
}
