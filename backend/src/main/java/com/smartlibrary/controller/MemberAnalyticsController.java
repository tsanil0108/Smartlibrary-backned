package com.smartlibrary.controller;

import com.smartlibrary.dto.MemberAnalyticsDto.*;

import com.smartlibrary.security.UserPrincipal;

import com.smartlibrary.service.MemberAnalyticsService;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
public class MemberAnalyticsController {

    private final MemberAnalyticsService analyticsService;

    // ==========================================
    // COMPLETE MEMBER DASHBOARD
    // ==========================================

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard(

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return analyticsService.getDashboard(
                me.getId()
        );
    }

    // ==========================================
    // READING STATISTICS
    // ==========================================

    @GetMapping("/analytics/stats")
    public ReadingStats getReadingStats(

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return analyticsService.getReadingStats(
                me.getId()
        );
    }

    // ==========================================
    // MONTHLY ACTIVITY
    // ==========================================

    @GetMapping("/analytics/monthly")
    public List<MonthlyActivity> getMonthlyActivity(

            @AuthenticationPrincipal UserPrincipal me,

            @RequestParam(defaultValue = "6")
            int months

    ) {

        return analyticsService.getMonthlyActivity(

                me.getId(),

                months
        );
    }

    // ==========================================
    // FAVOURITE CATEGORIES
    // ==========================================

    @GetMapping("/analytics/categories")
    public List<CategoryActivity> getFavouriteCategories(

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return analyticsService.getFavouriteCategories(
                me.getId()
        );
    }

    // ==========================================
    // RECENT READING ACTIVITY
    // ==========================================

    @GetMapping("/analytics/recent")
    public List<RecentActivity> getRecentActivity(

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return analyticsService.getRecentActivity(
                me.getId()
        );
    }

    // ==========================================
    // UPCOMING RETURNS
    // ==========================================

    @GetMapping("/analytics/upcoming")
    public List<UpcomingReturn> getUpcomingReturns(

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return analyticsService.getUpcomingReturns(
                me.getId()
        );
    }

    // ==========================================
    // ACCOUNT SUMMARY
    // ==========================================

    @GetMapping("/analytics/account")
    public AccountSummary getAccountSummary(

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return analyticsService.getAccountSummary(
                me.getId()
        );
    }

    // ==========================================
    // RECOMMENDED BOOKS
    // ==========================================

    @GetMapping("/analytics/recommendations")
    public List<RecommendedBook> getRecommendedBooks(

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return analyticsService.getRecommendedBooks(
                me.getId()
        );
    }
}