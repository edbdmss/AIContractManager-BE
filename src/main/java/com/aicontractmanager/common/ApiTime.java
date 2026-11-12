package com.aicontractmanager.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class ApiTime {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    private ApiTime() {}

    public static OffsetDateTime fromDatabase(LocalDateTime value) {
        return value == null ? null : value.atZone(API_ZONE).toOffsetDateTime();
    }
}
