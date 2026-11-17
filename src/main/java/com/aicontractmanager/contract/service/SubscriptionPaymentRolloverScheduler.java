package com.aicontractmanager.contract.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class SubscriptionPaymentRolloverScheduler {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionPaymentRolloverScheduler.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ContractService contracts;

    public SubscriptionPaymentRolloverScheduler(ContractService contracts) {
        this.contracts = contracts;
    }

    @Scheduled(cron = "${app.subscription-payment.rollover-cron:0 5 0 * * *}", zone = "Asia/Seoul")
    public void rollForwardDuePayments() {
        int updated = contracts.rollForwardDueSubscriptionPayments(LocalDate.now(KOREA_ZONE));
        if (updated > 0) {
            log.info("구독 다음 결제일 자동 갱신 완료: {}건", updated);
        }
    }
}
