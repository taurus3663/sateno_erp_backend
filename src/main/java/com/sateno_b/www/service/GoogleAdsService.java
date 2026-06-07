package com.sateno_b.www.service;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v24.services.ListAccessibleCustomersRequest;
import com.google.ads.googleads.v24.services.CustomerServiceClient;
import com.google.ads.googleads.v24.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v24.services.ListAccessibleCustomersResponse;
import com.google.ads.googleads.v24.services.SearchGoogleAdsRequest;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.oauth2.UserCredentials;
import com.sateno_b.www.model.entity.GoogleAdsCampaignName;
import com.sateno_b.www.model.entity.GoogleAdsEntity;
import com.sateno_b.www.model.entity.GoogleAdsRecordEntity;
import com.sateno_b.www.model.repository.GoogleAdsCampaignNameRepository;
import com.sateno_b.www.model.repository.GoogleAdsRecordRepository;
import com.sateno_b.www.model.repository.GoogleAdsRepository;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAdsService {

    private final GoogleAdsCampaignNameRepository googleAdsCampaignNameRepository;
    private final GoogleAdsRecordRepository googleAdsRecordRepository;
    @Value("${app.base-url}")
    private String baseUrl;

    private final GoogleAdsRepository googleAdsRepository;

    public List<String> getCampaignNames(long customerId) throws IOException {
        List<String> campaignNames = new ArrayList<>();

        // 1. Инициализация (чете автоматично от google-ads.properties)
        ClassPathResource resource = new ClassPathResource("google-ads.properties");
        GoogleAdsClient googleAdsClient = GoogleAdsClient.newBuilder()
                .fromPropertiesFile(resource.getFile())
                .build();

        // 2. Създаване на клиент за заявки
        try (GoogleAdsServiceClient googleAdsServiceClient = googleAdsClient.getLatestVersion().createGoogleAdsServiceClient()) {

            // 3. GAQL Заявка - тук взимаме имената и ID-тата
//            String query = "SELECT campaign.id, campaign.name FROM campaign WHERE campaign.status = 'ENABLED'";

            String query = "SELECT " +
                    "campaign.id, " +
                    "campaign.name, " +
                    "campaign.status, " +
                    "campaign.advertising_channel_type, " +
                    "metrics.clicks, " +
                    "metrics.impressions, " +
                    "metrics.cost_micros " +
                    "FROM campaign " +
                    "WHERE campaign.status = 'ENABLED'";
            SearchGoogleAdsRequest request = SearchGoogleAdsRequest.newBuilder()
                    .setCustomerId("3458720615")
                    .setQuery(query)
                    .build();

            // 4. Изпълнение и обработка на отговора
            googleAdsServiceClient.search(request).iterateAll().forEach(row -> {
                campaignNames.add(row.getCampaign().getName());
            });

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Грешка при комуникация с Google Ads: " + e.getMessage());
        }

        return campaignNames;
    }

    static final String SCOPE = "https://www.googleapis.com/auth/adwords";
    static final String ERP = "/api/ads/google/callback";

    // ── 1. Генерира URL → фронтендът го отваря ──────────────────
    public String genUrl(Long googleAdsId) throws IOException {
        GoogleAdsEntity entity = googleAdsRepository.findById(googleAdsId)
                .orElseThrow(() -> new IOException("Not found profile: " + googleAdsId));

        GoogleAuthorizationCodeFlow flow = buildFlow(
                entity.getClientId(),
                entity.getClientSecret()
        );

        String redirectUri = baseUrl + ERP;
        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(googleAdsId.toString())   // пазим ID-то
                .setApprovalPrompt("force")
                .build();
    }

    private GoogleAuthorizationCodeFlow buildFlow(String clientId, String clientSecret) throws IOException {
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(clientId);
        details.setClientSecret(clientSecret);

        GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
        clientSecrets.setInstalled(details);  // Desktop app — правилно за LocalServerReceiver

        return new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                List.of(SCOPE))
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();
    }

    // ── 2. Google ни праща тук след потвърждение ─────────────────
    public void handleCallback(String code, Long id) throws IOException {
        Long googleAdsId = id;

        GoogleAdsEntity entity = googleAdsRepository.findById(googleAdsId)
                .orElseThrow(() -> new IOException("Not found profile: " + googleAdsId));

        GoogleAuthorizationCodeFlow flow = buildFlow(
                entity.getClientId(),
                entity.getClientSecret()
        );

        String redirectUri = baseUrl + ERP;

        TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        String refreshToken = tokenResponse.getRefreshToken();
        entity.setRefreshToken(refreshToken);
        googleAdsRepository.save(entity);
    }

    public void listAccessible() throws IOException {
        ClassPathResource resource = new ClassPathResource("google-ads.properties");
        GoogleAdsClient client = GoogleAdsClient.newBuilder()
                .fromPropertiesFile(resource.getFile())
                .build();
        try (CustomerServiceClient svc =
                     client.getLatestVersion().createCustomerServiceClient()) {
            ListAccessibleCustomersResponse response =
                    svc.listAccessibleCustomers(
                            ListAccessibleCustomersRequest.newBuilder().build());
            response.getResourceNamesList()
                    .forEach(name -> System.out.println("Достъпен: " + name));
        }
    }

    public List<CampaignDetailsDTO> getCampaignDetails(long customerId) throws IOException {
        List<CampaignDetailsDTO> campaigns = new ArrayList<>();

        ClassPathResource resource = new ClassPathResource("google-ads.properties");
        GoogleAdsClient googleAdsClient = GoogleAdsClient.newBuilder()
                .fromPropertiesFile(resource.getFile())
                .build();
        try (GoogleAdsServiceClient googleAdsServiceClient = googleAdsClient.getLatestVersion().createGoogleAdsServiceClient()) {

//            String query = "SELECT campaign.id, campaign.name, campaign.status, " +
//                    "campaign.advertising_channel_type, metrics.clicks, " +
//                    "metrics.impressions, metrics.cost_micros " +
//                    "FROM campaign WHERE campaign.status = 'ENABLED'";
            // Промени заявката си така:
            String query = "SELECT campaign.id, campaign.name, " +
                            "customer.id, " +
                            "customer.time_zone, " +
                            "segments.date, " +
                            "segments.hour, " +
                            "metrics.clicks, " +
                            "metrics.impressions, " +
                            "metrics.cost_micros, " +
                            "metrics.conversions, " +
                            "metrics.ctr, " +
                            "metrics.average_cpc, " +
                            "metrics.average_cpm " +
                    "FROM campaign " +
                    "WHERE segments.date DURING LAST_30_DAYS " +
                    "AND campaign.status = 'ENABLED'";

            // Логика за предходния час
            LocalDateTime lastHour = LocalDateTime.now().minusHours(1);
            String date = lastHour.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            int hour = lastHour.getHour();

//            String query = String.format(
//                    "SELECT campaign.id, campaign.name, " +
//                            "customer.id, " +
//                            "customer.time_zone, " +
//                            "segments.date, " +
//                            "segments.hour, " +
//                            "metrics.clicks, " +
//                            "metrics.impressions, " +
//                            "metrics.cost_micros, " +
//                            "metrics.conversions, " +
//                            "metrics.ctr, " +
//                            "metrics.average_cpc, " +
//                            "metrics.average_cpm " +
//                            "FROM campaign " +
//                            "WHERE segments.date = '%s' AND segments.hour = %d " +
//                            "AND campaign.status = 'ENABLED'", date, hour);

            SearchGoogleAdsRequest request = SearchGoogleAdsRequest.newBuilder()
                    .setCustomerId(String.valueOf(customerId)) // Използвай параметъра
                    .setQuery(query)
                    .build();

            googleAdsServiceClient.search(request).iterateAll().forEach(row -> {
                CampaignDetailsDTO dto = new CampaignDetailsDTO();
                dto.setId(String.valueOf(row.getCampaign().getId()));
                dto.setName(row.getCampaign().getName());
                dto.setStatus(row.getCampaign().getStatus().name());
                dto.setClicks(row.getMetrics().getClicks());
                dto.setImpressions(row.getMetrics().getImpressions());
                dto.setDate(row.getSegments().getDate()); // Ново поле за дата
                dto.setHour(String.valueOf(row.getSegments().getHour())); // Ново поле за час (0-23)
                // Превръщаме микросите в нормална валута
                dto.setSpend(row.getMetrics().getCostMicros() / 1_000_000.0);
                dto.setCpc(row.getMetrics().getAverageCpc());
                dto.setCtr(row.getMetrics().getAverageCpm());
                dto.setCpm(row.getMetrics().getCtr());

//                System.out.println(TextFormat.printer().printToString(row));

                campaigns.add(dto);
            });
        }
        return campaigns;
    }
    @Data
    public class CampaignDetailsDTO {
        private String id;
        private String name;
        private String status;
//        private String channelType;
        private Double cpc;
        private Double cpm;
        private Double ctr;
        private long clicks;
        private long impressions;
        private Double spend; // в реални пари
        private String date;
        private String hour;
    }

public void triggerBackfillForNewAccount(GoogleAdsEntity account) {

    boolean existsRecordByAd = googleAdsRecordRepository.existsGoogleAdsRecordEntityByAd(account);
    if (existsRecordByAd) {
        return;
    }

    GoogleAdsClient googleAdsClient = GoogleAdsClient.newBuilder()
            .setDeveloperToken(account.getDeveloperToken())
            .setLoginCustomerId(Long.parseLong(account.getLoginCustomerId()))
            .setCredentials(
                    UserCredentials.newBuilder()
                            .setClientId(account.getClientId())
                            .setClientSecret(account.getClientSecret())
                            .setRefreshToken(account.getRefreshToken())
                            .build()
            )
            .build();

    String query = "SELECT campaign.id, campaign.name, " +
            "customer.id, " +
            "customer.time_zone, " +
            "segments.date, " +
            "segments.hour, " +
            "metrics.clicks, " +
            "metrics.impressions, " +
            "metrics.cost_micros, " +
            "metrics.conversions, " +
            "metrics.ctr, " +
            "metrics.average_cpc, " +
            "metrics.average_cpm " +
            "FROM campaign " +
            "WHERE segments.date DURING LAST_30_DAYS " +
            "AND campaign.status = 'ENABLED'";

    try (GoogleAdsServiceClient googleAdsServiceClient =
                 googleAdsClient.getLatestVersion().createGoogleAdsServiceClient()) {

        SearchGoogleAdsRequest request = SearchGoogleAdsRequest.newBuilder()
                .setCustomerId(account.getLoginCustomerId())
                .setQuery(query)
                .build();

        final String[] detectedTimeZone = {null};
        AtomicReference<ZoneId> accountZone = new AtomicReference<>(
                (account.getTimeZone() != null) ? ZoneId.of(account.getTimeZone()) : null
        );
        googleAdsServiceClient.search(request).iterateAll().forEach(row -> {
            if (detectedTimeZone[0] == null || accountZone.get() == null) {
                detectedTimeZone[0] = row.getCustomer().getTimeZone();
                accountZone.set(ZoneId.of(row.getCustomer().getTimeZone()));
            }
            String campaignName = row.getCampaign().getName();
            GoogleAdsCampaignName googleAdsCampaignName = googleAdsCampaignNameRepository
                    .findByName(campaignName)
                    .orElseGet(() -> {
                        GoogleAdsCampaignName cn = new GoogleAdsCampaignName();
                        cn.setName(campaignName);
                        return googleAdsCampaignNameRepository.save(cn);
                    });

            String dateStr = row.getSegments().getDate();
            int hour = row.getSegments().getHour();

            Instant recordedAt = LocalDateTime.of(LocalDate.parse(dateStr), LocalTime.of(hour, 0))
                    .atZone(accountZone.get()) // Тук казваме: "Този час е в София"
                    .toInstant();        // Java го превръща в UTC момента

            GoogleAdsRecordEntity record = googleAdsRecordRepository
                    .findByAdAndCampaignNameAndRecordedAt(account, googleAdsCampaignName, recordedAt)
                    .orElse(new GoogleAdsRecordEntity());

            record.setRecordedAt(recordedAt);
            record.setClicks((int) row.getMetrics().getClicks());
            record.setImpressions((int) row.getMetrics().getImpressions());
            record.setSpend(row.getMetrics().getCostMicros() / 1_000_000.0);
            record.setCtr(row.getMetrics().getCtr());
            record.setCpc(row.getMetrics().getAverageCpc() / 1_000_000.0);
            record.setCpm(row.getMetrics().getAverageCpm() / 1_000_000.0);
            record.setAd(account);
            record.setCampaignName(googleAdsCampaignName);

            googleAdsRecordRepository.save(record);
        });
        if (detectedTimeZone[0] != null && account.getTimeZone() == null) {
            account.setTimeZone(detectedTimeZone[0]);
            googleAdsRepository.save(account);
        }
    }
}
    @Scheduled(cron = "0 5 * * * *")
    public void syncPreviousHour() {

        List<GoogleAdsEntity> adsRepositoryAll = googleAdsRepository.findAllByActiveTrue();

        for (GoogleAdsEntity account : adsRepositoryAll) {
            try {
                GoogleAdsClient googleAdsClient = GoogleAdsClient.newBuilder()
                        .setDeveloperToken(account.getDeveloperToken())
                        .setLoginCustomerId(Long.parseLong(account.getLoginCustomerId()))
                        .setCredentials(
                                UserCredentials.newBuilder()
                                        .setClientId(account.getClientId())
                                        .setClientSecret(account.getClientSecret())
                                        .setRefreshToken(account.getRefreshToken())
                                        .build()
                        )
                        .build();

//                LocalDateTime lastHour = LocalDateTime.now().minusHours(1);
//                String date = lastHour.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
//                int hour = lastHour.getHour();
                String tzString = account.getTimeZone();
                ZoneId zone = ZoneId.of(tzString);

                ZonedDateTime lastHour = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1).withZoneSameInstant(zone);

                String date = lastHour.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                int hour = lastHour.getHour();

                String query = String.format(
                        "SELECT campaign.id, campaign.name, " +
                                "customer.id, " +
                                "customer.time_zone, " +
                                "segments.date, " +
                                "segments.hour, " +
                                "metrics.clicks, " +
                                "metrics.impressions, " +
                                "metrics.cost_micros, " +
                                "metrics.conversions, " +
                                "metrics.ctr, " +
                                "metrics.average_cpc, " +
                                "metrics.average_cpm " +
                                "FROM campaign " +
                                "WHERE segments.date = '%s' AND segments.hour = %d " +
                                "AND campaign.status = 'ENABLED'", date, hour);

                try (GoogleAdsServiceClient googleAdsServiceClient =
                             googleAdsClient.getLatestVersion().createGoogleAdsServiceClient()) {

                    SearchGoogleAdsRequest request = SearchGoogleAdsRequest.newBuilder()
                            .setCustomerId(account.getLoginCustomerId())
                            .setQuery(query)
                            .build();

                    googleAdsServiceClient.search(request).iterateAll().forEach(row -> {

                        String campaignName = row.getCampaign().getName();
                        GoogleAdsCampaignName googleAdsCampaignName = googleAdsCampaignNameRepository
                                .findByName(campaignName)
                                .orElseGet(() -> {
                                    GoogleAdsCampaignName cn = new GoogleAdsCampaignName();
                                    cn.setName(campaignName);
                                    return googleAdsCampaignNameRepository.save(cn);
                                });

                        String dateStr = row.getSegments().getDate();
                        int hourr = row.getSegments().getHour();
                        String customerTimeZone = row.getCustomer().getTimeZone();

                        Instant recordedAt = LocalDateTime.of(LocalDate.parse(dateStr), LocalTime.of(hourr, 0))
                                .atZone(zone) // Използвай същата ZoneId 'zone'
                                .toInstant();

                        GoogleAdsRecordEntity record = googleAdsRecordRepository
                                .findByAdAndCampaignNameAndRecordedAt(account, googleAdsCampaignName, recordedAt)
                                .orElse(new GoogleAdsRecordEntity());

                        record.setRecordedAt(recordedAt);
                        record.setClicks((int) row.getMetrics().getClicks());
                        record.setImpressions((int) row.getMetrics().getImpressions());
                        record.setSpend(row.getMetrics().getCostMicros() / 1_000_000.0);
                        record.setCtr(row.getMetrics().getCtr());
                        record.setCpc(row.getMetrics().getAverageCpc() / 1_000_000.0);
                        record.setCpm(row.getMetrics().getAverageCpm() / 1_000_000.0);
                        record.setAd(account);
                        record.setCampaignName(googleAdsCampaignName);

                        googleAdsRecordRepository.save(record);
                    });
                }

            } catch (Exception e) {
                log.error("Грешка при синхронизация на акаунт {}: {}",
                        account.getLoginCustomerId(), e.getMessage());
            }
        }
    }

@Scheduled(cron = "0 0 0/3 * * *")
public void runRecoverySync() {
    syncMissingHours();
}

    @Transactional
    public void syncMissingHours() {
        List<GoogleAdsEntity> activeAccounts = googleAdsRepository.findAll();

        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS)
                .minus(2, ChronoUnit.HOURS);

        for (GoogleAdsEntity account : activeAccounts) {
            try {
                Instant lastRecorded = googleAdsRecordRepository.findLatestRecordedAt(account)
                        .orElse(now.minus(24, ChronoUnit.HOURS));

                Instant recoveryFrom = now.minus(24, ChronoUnit.HOURS);
                Instant cursor = lastRecorded.isBefore(recoveryFrom) ? recoveryFrom : lastRecorded.plus(1, ChronoUnit.HOURS);

                while (!cursor.isAfter(now)) {
                    log.info("[{}] Наваксване на пропуснат час: {}", account.getLoginCustomerId(), cursor);
                    try {
                        syncInstant(account, cursor);
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (Exception e) {
                        log.error("[{}] Грешка при наваксване на {}: {}",
                                account.getLoginCustomerId(), cursor, e.getMessage());
                        break;
                    }
                    cursor = cursor.plus(1, ChronoUnit.HOURS);
                }
            } catch (Exception e) {
                log.error("Грешка при recovery за акаунт {}: {}",
                        account.getLoginCustomerId(), e.getMessage());
            }
        }
    }

    private void syncInstant(GoogleAdsEntity account, Instant targetInstant) {
        String tzString = account.getTimeZone();
        ZoneId accountZone = ZoneId.of(tzString);

        GoogleAdsClient googleAdsClient = GoogleAdsClient.newBuilder()
                .setDeveloperToken(account.getDeveloperToken())
                .setLoginCustomerId(Long.parseLong(account.getLoginCustomerId()))
                .setCredentials(
                        UserCredentials.newBuilder()
                                .setClientId(account.getClientId())
                                .setClientSecret(account.getClientSecret())
                                .setRefreshToken(account.getRefreshToken())
                                .build()
                )
                .build();

        ZonedDateTime zdt = targetInstant.atZone(accountZone);
        String dateStr = zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        int hour = zdt.getHour();

        String query = String.format(
                "SELECT campaign.id, campaign.name, " +
                        "customer.id, " +
                        "customer.time_zone, " +
                        "segments.date, " +
                        "segments.hour, " +
                        "metrics.clicks, " +
                        "metrics.impressions, " +
                        "metrics.cost_micros, " +
                        "metrics.conversions, " +
                        "metrics.ctr, " +
                        "metrics.average_cpc, " +
                        "metrics.average_cpm " +
                        "FROM campaign " +
                        "WHERE segments.date = '%s' AND segments.hour = %d " +
                        "AND campaign.status = 'ENABLED'", dateStr, hour);

        try (GoogleAdsServiceClient googleAdsServiceClient =
                     googleAdsClient.getLatestVersion().createGoogleAdsServiceClient()) {

            SearchGoogleAdsRequest request = SearchGoogleAdsRequest.newBuilder()
                    .setCustomerId(account.getLoginCustomerId())
                    .setQuery(query)
                    .build();

            googleAdsServiceClient.search(request).iterateAll().forEach(row -> {
                String campaignName = row.getCampaign().getName();
                GoogleAdsCampaignName googleAdsCampaignName = googleAdsCampaignNameRepository
                        .findByName(campaignName)
                        .orElseGet(() -> {
                            GoogleAdsCampaignName cn = new GoogleAdsCampaignName();
                            cn.setName(campaignName);
                            return googleAdsCampaignNameRepository.save(cn);
                        });

                Instant recordedAt = targetInstant;

                GoogleAdsRecordEntity record = googleAdsRecordRepository
                        .findByAdAndCampaignNameAndRecordedAt(account, googleAdsCampaignName, recordedAt)
                        .orElse(new GoogleAdsRecordEntity());

                record.setRecordedAt(recordedAt);
                record.setClicks((int) row.getMetrics().getClicks());
                record.setImpressions((int) row.getMetrics().getImpressions());
                record.setSpend(row.getMetrics().getCostMicros() / 1_000_000.0);
                record.setCtr(row.getMetrics().getCtr());
                record.setCpc(row.getMetrics().getAverageCpc() / 1_000_000.0);
                record.setCpm(row.getMetrics().getAverageCpm() / 1_000_000.0);
                record.setAd(account);
                record.setCampaignName(googleAdsCampaignName);

                googleAdsRecordRepository.save(record);
            });
        }
    }
}
