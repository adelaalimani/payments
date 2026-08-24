package com.adela.payments.config;

import java.math.BigDecimal;
import java.util.Map;

public record AnalyticsResponse(

        long totalTransactions,
        BigDecimal totalVolume,
        double successRate,
        Map<String, MethodBreakdown> breakdownByMethod
) {
    public record MethodBreakdown(
            long count,
            BigDecimal volume
    ) {
    }
}