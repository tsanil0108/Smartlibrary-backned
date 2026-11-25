package com.smartlibrary.controller;

import com.smartlibrary.dto.LoanDto.LoanResponse;
import com.smartlibrary.dto.PageResponse;
import com.smartlibrary.dto.ReportDto.AuditLogResponse;
import com.smartlibrary.dto.ReportDto.CategoryStat;
import com.smartlibrary.dto.ReportDto.DashboardResponse;
import com.smartlibrary.dto.ReportDto.InventoryStatus;
import com.smartlibrary.dto.ReportDto.MonthStat;
import com.smartlibrary.service.LoanService;
import com.smartlibrary.service.ReportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final LoanService loanService;

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return reportService.dashboard();
    }

    @GetMapping("/most-read-categories")
    public List<CategoryStat> mostReadCategories() {
        return reportService.mostReadCategories();
    }

    @GetMapping("/borrowing-trends")
    public List<MonthStat> borrowingTrends(@RequestParam(defaultValue = "12") int months) {
        return reportService.borrowingTrends(months);
    }

    @GetMapping("/inventory")
    public InventoryStatus inventory() {
        return reportService.inventory();
    }

    @GetMapping("/overdue")
    public PageResponse<LoanResponse> overdue(
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return loanService.find(null, null, true, pageable);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reportService.auditLogs(pageable);
    }
}
