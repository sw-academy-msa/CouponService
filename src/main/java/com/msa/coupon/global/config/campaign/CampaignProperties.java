package com.msa.coupon.global.config.campaign;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.UUID;

/** 시연 캠페인은 허용 목록 계정에만 발급하며 목록이 비었거나 형식이 틀리면 모두 막습니다. */
@ConfigurationProperties("coupon.campaign")
public record CampaignProperties(String demoAccountIds) {
    private static final int DEMO_ACCOUNT_LIMIT = 10;

    public boolean demoAllowed(UUID account) {
        if (demoAccountIds == null || demoAccountIds.isBlank()) return false;
        try {
            var ids =
                    Arrays.stream(demoAccountIds.split(","))
                            .map(String::trim)
                            .map(UUID::fromString)
                            .toList();
            return ids.size() <= DEMO_ACCOUNT_LIMIT && ids.contains(account);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "CampaignProperties[REDACTED]";
    }
}
