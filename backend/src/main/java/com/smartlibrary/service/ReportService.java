package com.smartlibrary.service;

import com.smartlibrary.dto.PageResponse;
import com.smartlibrary.dto.ReportDto.AuditLogResponse;
import com.smartlibrary.dto.ReportDto.CategoryStat;
import com.smartlibrary.dto.ReportDto.DashboardResponse;
import com.smartlibrary.dto.ReportDto.InventoryStatus;
import com.smartlibrary.dto.ReportDto.MonthStat;
import com.smartlibrary.dto.projection.CategoryCount;
import com.smartlibrary.entity.AuditLog;
import com.smartlibrary.entity.CopyStatus;
import com.smartlibrary.entity.LoanStatus;
import com.smartlibrary.entity.Role;
import com.smartlibrary.repository.AuditLogRepository;
import com.smartlibrary.repository.BookCopyRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.LoanRepository;
import com.smartlibrary.repository.MembershipRepository;
import com.smartlibrary.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All numbers come straight from the database, never from cached or client-side values. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final LoanRepository loanRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public DashboardResponse dashboard() {
        LocalDate today = LocalDate.now(clock);
        return new DashboardResponse(
                bookRepository.countByArchivedFalse(),
                copyRepository.count(),
                copyRepository.countByStatus(CopyStatus.AVAILABLE),
                userRepository.countByRole(Role.MEMBER),
                membershipRepository.countActiveMembers(today),
                loanRepository.countByStatus(LoanStatus.ACTIVE),
                loanRepository.countByStatusAndDueDateBefore(LoanStatus.ACTIVE, today));
    }

    public List<CategoryStat> mostReadCategories() {
        List<CategoryCount> rows = loanRepository.countLoansByCategory();
        long total = rows.stream().mapToLong(CategoryCount::loans).sum();
        return rows.stream()
                .map(r -> new CategoryStat(r.category(), r.loans(),
                        total == 0 ? 0 : Math.round(r.loans() * 10000.0 / total) / 100.0))
                .toList();
    }

    public List<MonthStat> borrowingTrends(int months) {
        LocalDate from = LocalDate.now(clock).minusMonths(Math.max(1, Math.min(months, 36))).withDayOfMonth(1);
        return loanRepository.monthlyTrend(from).stream()
                .map(t -> new MonthStat(t.year(), t.month(), t.loans())).toList();
    }

    public InventoryStatus inventory() {
        Map<CopyStatus, Long> raw = new EnumMap<>(CopyStatus.class);
        for (CopyStatus s : CopyStatus.values()) raw.put(s, 0L);
        copyRepository.countGroupedByStatus().forEach(row -> raw.put((CopyStatus) row[0], (Long) row[1]));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        raw.forEach((k, v) -> byStatus.put(k.name(), v));
        return new InventoryStatus(byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus);
    }

    public PageResponse<AuditLogResponse> auditLogs(Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findAll(pageable);
        return PageResponse.of(page.getContent().stream().map(AuditLogResponse::from).toList(), page);
    }
}
