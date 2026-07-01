package com.sateno_b.www.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Обобщен профил „Поведение на клиента" (дашборд по дизайна).
 * Агрегира се от Live сесии/събития + изоставени каси + Lead Score за един клиент.
 *
 * ЗАБЕЛЕЖКА: полетата {@code totalTimeText} и {@code locations} изискват проследяване,
 * което още не се събира (общо време на сайта, geo резолюция) — засега са празни/приблизителни.
 */
@Data
public class CustomerBehaviorDto {

    // --- профил ---
    private Long customerId;
    private String name;
    private String phone;
    private String email;
    private Instant firstSeen;
    private Instant lastSeen;
    private Integer totalVisits;
    private String totalTimeText;   // засега null — не се събира
    private Integer leadScore;
    private String leadTier;
    private String source;          // напр. "Facebook Ads" (от UTM/referrer)
    private String sourceCampaign;  // utm_campaign

    // --- KPI ---
    private int pageviews;
    private int addToCarts;
    private int abandonedCount;
    private BigDecimal abandonedValue = BigDecimal.ZERO;
    private int checkoutStarts;
    private int completedOrders;
    private BigDecimal completedValue = BigDecimal.ZERO;
    private String currency;

    // --- разпределения ---
    private List<Slice> topCategories = new ArrayList<>();
    private List<Slice> topProducts = new ArrayList<>();
    private List<FunnelStep> funnel = new ArrayList<>();
    private List<Slice> devices = new ArrayList<>();
    private List<Slice> locations = new ArrayList<>();      // засега празно (geo)
    private List<Slice> trafficSources = new ArrayList<>();
    private List<Activity> timeline = new ArrayList<>();
    private List<Abandoned> abandoned = new ArrayList<>();

    @Data
    public static class Slice {
        private String label;
        private int count;
        private int pct;
        public Slice() {}
        public Slice(String label, int count, int pct) { this.label = label; this.count = count; this.pct = pct; }
    }

    @Data
    public static class FunnelStep {
        private String label;
        private int count;
        private int pct;
        public FunnelStep() {}
        public FunnelStep(String label, int count, int pct) { this.label = label; this.count = count; this.pct = pct; }
    }

    @Data
    public static class Activity {
        private Instant time;
        private String type;   // visitor|product|category|cart|checkout|order|leave
        private String title;
        private String sub;
        private String device;
    }

    @Data
    public static class Abandoned {
        private Instant date;
        private int products;
        private BigDecimal value;
        private String currency;
    }
}
