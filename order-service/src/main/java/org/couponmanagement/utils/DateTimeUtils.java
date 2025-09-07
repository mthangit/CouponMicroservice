package org.couponmanagement.utils;

import io.micrometer.observation.annotation.Observed;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DateTimeUtils {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Observed(name = "parseOrderDate", contextualName = "DateTimeUtils.parseOrderDate")
    @PerformanceMonitor
    public LocalDateTime parseOrderDate(String orderDateStr) {
        return LocalDateTime.parse(orderDateStr, FORMATTER);
    }
}
