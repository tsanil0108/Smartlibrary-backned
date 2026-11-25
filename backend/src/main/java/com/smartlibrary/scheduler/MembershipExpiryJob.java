package com.smartlibrary.scheduler;

import com.smartlibrary.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class MembershipExpiryJob {

    private final MembershipService membershipService;

    @Scheduled(cron = "0 5 0 * * *", zone = "${app.timezone:Asia/Kolkata}")
    public void expireMemberships() {
        membershipService.expireEndedMemberships();
    }
}
