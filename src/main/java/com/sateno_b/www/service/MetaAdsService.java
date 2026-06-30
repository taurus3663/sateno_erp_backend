package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.MetaAdsCampaignName;
import com.sateno_b.www.model.entity.MetaAdsEntity;
import com.sateno_b.www.model.entity.MetaAdsRecordEntity;
import com.sateno_b.www.model.repository.MetaAdsCampaignNameRepository;
import com.sateno_b.www.model.repository.MetaAdsRecordRepository;
import com.sateno_b.www.model.repository.MetaAdsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetaAdsService {

    private static final ZoneId META_ZONE = ZoneId.of("Europe/Sofia");
    private final RestClient restClient;
    private static final String API_VERSION = "v25.0";
    private static final String BASE_URL = "https://graph.facebook.com/" + API_VERSION + "/";
    private final MetaAdsRepository metaAdsRepository;
    private final MetaAdsRecordRepository metaAdsRecordRepository;
    private final MetaAdsCampaignNameRepository metaAdsCampaignNameRepository;

    // А) Списък с всички рекламни акаунти, до които имаш достъп
    public Map<String, Object> getMyAdAccounts(String accessToken) {
        return fetchFromMeta("me/adaccounts", accessToken);
    }

    // Б) Статистика за конкретен рекламен акаунт (Insights)
    public Map<String, Object> getCampaignInsights(String adAccountId, String accessToken) {
        String accountId = adAccountId.startsWith("act_") ? adAccountId : "act_" + adAccountId;

        LocalDate today = LocalDate.now();
        String todayStr = today.toString(); // "2026-05-29"

        String endpoint = accountId + "/insights?level=campaign" +
                "&fields=campaign_name,spend,clicks,impressions,cpc,cpm,ctr,date_start,date_stop" +
                "&time_range[since]=" + todayStr +
                "&time_range[until]=" + todayStr +
                "&time_increment=1" +
                "&breakdowns=hourly_stats_aggregated_by_advertiser_time_zone" +
                "&limit=9999";
//        String endpoint = accountId + "/insights?level=campaign" +
//                "&fields=campaign_name,spend,clicks,impressions,cpc,cpm,ctr,date_start,date_stop" +
//                "&date_preset=last_90d" +
//                "&time_increment=1" +
//                "&breakdowns=hourly_stats_aggregated_by_advertiser_time_zone" +
//                "&limit=9999" +
//                "&order=desc";
        return fetchFromMeta(endpoint, accessToken);
    }

    // В) Универсален метод за заявки (избягваш повторението на хедърите)
    private Map<String, Object> fetchFromMeta(String endpoint, String accessToken) {
        return restClient.get()
                .uri(BASE_URL + endpoint)
                .headers(h -> {
                    h.setBearerAuth(accessToken);
                    h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("text/javascript")));
                })
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }


    // МЕТОД ЗА ИСТОРИЧЕСКИ ДАННИ ЗА КОНКРЕТЕН ЧАС
    public Map<String, Object> getInsightsForHour(MetaAdsEntity ad, Instant targetInstant) {
        String accountId = ad.getAdAccountId().startsWith("act_") ? ad.getAdAccountId() : "act_" + ad.getAdAccountId();

        // Превръщаме Instant в ISO формат, подходящ за Meta API
        String dateStr = targetInstant.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // ВАЖНО: Тук подаваме конкретен time_range за този час
        String endpoint = accountId + "/insights?level=campaign" +
                "&fields=campaign_name,spend,clicks,impressions,cpc,cpm,ctr" +
                "&time_range={'since':'" + dateStr + "','until':'" + dateStr + "'}";

        return fetchFromMeta(endpoint, ad.getAccessToken());
    }

    // БЕКФИЛ ЛОГИКА (Задача А)
//    @Scheduled(fixedDelay = 60000) // Всяка минута
//    public void runBackfill() {
//        List<MetaAdsEntity> activeAccounts = metaAdsRepository.findAllByActiveTrue();
//        Instant now = Instant.now();
//
//        for (MetaAdsEntity account : activeAccounts) {
//            // 1. Взимаме последния запис по createdAt
//            Instant lastSync = metaAdsRecordRepository.findLatestCreatedAt(account)
//                    .orElse(Instant.parse("2026-01-01T00:00:00Z"));
//
//            // 2. ЗАКРЪГЛЯНЕ: Взимаме само часа, за да не се влияем от минутите на записване
//            Instant lastHourSync = lastSync.truncatedTo(ChronoUnit.HOURS);
//            Instant nextHourToSync = lastHourSync.plus(1, ChronoUnit.HOURS);
//
//            // 3. Проверка: Теглим само ако следващият час е в миналото (преди "сега")
//            if (nextHourToSync.isBefore(now.truncatedTo(ChronoUnit.HOURS))) {
//
//                try {
//                    // Изтегляме данни за този конкретен час
//                    Map<String, Object> data = getInsightsForHour(account, nextHourToSync);
//
//                    MetaAdsRecordEntity r = new MetaAdsRecordEntity();
//                    r.setAd(account);
//                    r.setCtr((Double) data.get("ctr"));
//                    r.setCpm((Double) data.get("cpm"));
//                    r.setCpc((Double) data.get("cpc"));
//                    r.setClicks(Math.toIntExact((Long) data.get("clicks")));
//                    r.setSpend((Double) data.get("spend"));
//                    r.setImpressions(Math.toIntExact((Long) data.get("impressions")));
//                    metaAdsRecordRepository.saveAndFlush(r);
//
//                    log.info("Успешно запълнен час: {}", nextHourToSync);
//                    break; // Спираме за този цикъл, за да не претоварим API-то
//                } catch (Exception e) {
//                    log.error("Грешка за час {}: {}", nextHourToSync, e.getMessage());
//                    break;
//                }
//            }
//        }
//    }



    // ===================================================
    // НОВ АКАУНТ / RESYNC — backfill ден по ден (1 API call на ден, всички часове)
    // ===================================================
    public void triggerBackfillForNewAccount(MetaAdsEntity account) {
        LocalDate today = LocalDate.now(META_ZONE);
        LocalDate from = today.minusYears(3);
        log.info("[{}] Стартиран backfill от {} до {}", account.getName(), from, today.minusDays(1));

        for (LocalDate date = from; date.isBefore(today); date = date.plusDays(1)) {
            try {
                Map<String, Object> response = getInsightsForDate(account, date.toString());
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
                if (dataList != null && !dataList.isEmpty()) {
                    for (Map<String, Object> row : dataList) {
                        int hour = parseHour((String) row.get("hourly_stats_aggregated_by_advertiser_time_zone"));
                        Instant recordedAt = date.atStartOfDay(META_ZONE).plusHours(hour).toInstant();
                        saveRecord(account, row, recordedAt);
                    }
                }
                log.info("[{}] Запълнен ден: {}", account.getName(), date);
                TimeUnit.MILLISECONDS.sleep(300);
            } catch (Exception e) {
                log.error("[{}] Грешка за {}: {}", account.getName(), date, e.getMessage());
            }
        }
        log.info("[{}] Backfill завършен", account.getName());
    }

    // ===================================================
    // ВСЕКИ ЧАС — тегли предишния час
    // в 13:00 тегли 12:00-12:59
    // ===================================================
    @Scheduled(cron = "0 5 * * * *")
    public void syncPreviousHour() {
        Instant previousHour = Instant.now()
                .truncatedTo(ChronoUnit.HOURS)
                .minus(1, ChronoUnit.HOURS);

        log.info("Синхронизиране на час: {}", previousHour);

        List<MetaAdsEntity> activeAccounts = metaAdsRepository.findAllByActiveTrue();
        for (MetaAdsEntity account : activeAccounts) {
            try {
                syncInstant(account, previousHour);
            } catch (Exception e) {
                log.error("[{}] Грешка: {}", account.getName(), e.getMessage());
            }
        }
    }

    // "Чистач" на пропуски (на всеки 30 минути)
    @Scheduled(cron = "0 0 23 * * *")
    public void runRecoverySync() {
        syncMissingHours();
    }

    // ===================================================
    // СИНХРОНИЗИРА КОНКРЕТЕН Instant (ден или час)
    // ===================================================
    @Transactional
    public void syncInstant(MetaAdsEntity account, Instant targetInstant) throws Exception {
        // Извличаме датата от Instant за API заявката
        String dateStr = targetInstant
                .atZone(META_ZONE)
                .toLocalDate()
                .toString(); // "2026-05-29"

        Map<String, Object> response = getInsightsForDate(account, dateStr);
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
        if (dataList == null || dataList.isEmpty()) return;

        for (Map<String, Object> row : dataList) {
            String hourStr = (String) row.get("hourly_stats_aggregated_by_advertiser_time_zone");
            int hour = parseHour(hourStr); // "12:00:00 - 12:59:59" → 12

            // Конструираме точния Instant за този час
            Instant recordedAt = targetInstant
                    .atZone(META_ZONE)
                    .toLocalDate()
                    .atStartOfDay(META_ZONE)
                    .plusHours(hour)
                    .toInstant();

            // Ако синхронизираме целия ден (backfill) — вземаме всички часове
            // Ако синхронизираме конкретен час — пропускаме останалите
            boolean isHourlySync = targetInstant.equals(targetInstant.truncatedTo(ChronoUnit.HOURS))
                    && !targetInstant.equals(targetInstant.truncatedTo(ChronoUnit.DAYS));

            if (isHourlySync && !recordedAt.equals(targetInstant)) {
                continue;
            }

            saveRecord(account, row, recordedAt);
        }
    }

    // ===================================================
    // ЗАПИСВА ЕДИН РЕД
    // ===================================================
    private void saveRecord(MetaAdsEntity account, Map<String, Object> row, Instant recordedAt) {
        String campaignNameStr = (String) row.get("campaign_name");

        MetaAdsCampaignName campaignName = metaAdsCampaignNameRepository
                .findByName(campaignNameStr)
                .orElseGet(() -> {
                    MetaAdsCampaignName cn = new MetaAdsCampaignName();
                    cn.setName(campaignNameStr);
                    return metaAdsCampaignNameRepository.save(cn);
                });

        if (metaAdsRecordRepository.existsByAdAndRecordedAtAndCampaignName(
                account, recordedAt, campaignName)) {
            return;
        }

        MetaAdsRecordEntity record = new MetaAdsRecordEntity();
        record.setAd(account);
        record.setCampaignName(campaignName);
        record.setRecordedAt(recordedAt);
        record.setSpend(parseDouble(row.get("spend")));
        record.setClicks(parseInt(row.get("clicks")));
        record.setImpressions(parseInt(row.get("impressions")));
        record.setCpc(parseDouble(row.get("cpc")));
        record.setCpm(parseDouble(row.get("cpm")));
        record.setCtr(parseDouble(row.get("ctr")));

        metaAdsRecordRepository.save(record);
    }

    // ===================================================
    // API ЗАЯВКА
    // ===================================================
    private Map<String, Object> getInsightsForDate(MetaAdsEntity ad, String dateStr) {
        String accountId = ad.getAdAccountId().startsWith("act_")
                ? ad.getAdAccountId() : "act_" + ad.getAdAccountId();

        String endpoint = accountId + "/insights?level=campaign" +
                "&fields=campaign_name,spend,clicks,impressions,cpc,cpm,ctr,date_start,date_stop" +
                "&time_range[since]=" + dateStr +
                "&time_range[until]=" + dateStr +
                "&time_increment=1" +
                "&breakdowns=hourly_stats_aggregated_by_advertiser_time_zone" +
                "&limit=9999";

        return fetchFromMeta(endpoint, ad.getAccessToken());
    }

    // ===================================================
    // HELPERS
    // ===================================================
    private int parseHour(String hourStr) {
        if (hourStr == null) return 0;
        return Integer.parseInt(hourStr.substring(0, 2));
    }

    private Double parseDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (Exception e) { return 0.0; }
    }

    private Integer parseInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (Exception e) { return 0; }
    }


    // Този метод извикваш в @Scheduled вместо или заедно със syncPreviousHour
    @Transactional
    public void syncMissingHours() {
        List<MetaAdsEntity> activeAccounts = metaAdsRepository.findAllByActiveTrue();
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .minus(1, ChronoUnit.HOURS);
        Instant recoveryFrom = now.minus(24, ChronoUnit.HOURS);

        for (MetaAdsEntity account : activeAccounts) {
            Set<Instant> existingHours = new HashSet<>(
                    metaAdsRecordRepository.findDistinctRecordedAtInRange(account, recoveryFrom, now)
            );

            Instant cursor = recoveryFrom;
            while (!cursor.isAfter(now)) {
                if (!existingHours.contains(cursor)) {
                    log.info("[{}] Наваксване на пропуснат час: {}", account.getName(), cursor);
                    try {
                        syncInstant(account, cursor);
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (Exception e) {
                        log.error("[{}] Грешка при наваксване на {}: {}",
                                account.getName(), cursor, e.getMessage());
                        break;
                    }
                }
                cursor = cursor.plus(1, ChronoUnit.HOURS);
            }
        }
    }
}