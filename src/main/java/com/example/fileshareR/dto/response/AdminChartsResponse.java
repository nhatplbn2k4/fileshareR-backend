package com.example.fileshareR.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminChartsResponse {
    private List<DailyPoint> signupsLast30Days;
    private List<DailyPoint> revenueLast30Days;
    private List<MonthlyPoint> revenueLast12Months;
    private List<LabeledCount> documentsByType;
    private List<LabeledCount> paymentsByStatus;
    private List<LabeledCount> usersByPlan;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPoint {
        private String date;   // ISO yyyy-MM-dd
        private long value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPoint {
        private String month;  // yyyy-MM
        private long value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabeledCount {
        private String label;
        private long count;
    }
}
