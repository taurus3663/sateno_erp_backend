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
import com.google.protobuf.TextFormat;
import com.sateno_b.www.model.entity.GoogleAdsCampaignName;
import com.sateno_b.www.model.entity.GoogleAdsEntity;
import com.sateno_b.www.model.entity.GoogleAdsRecordEntity;
import com.sateno_b.www.model.repository.GoogleAdsCampaignNameRepository;
import com.sateno_b.www.model.repository.GoogleAdsRecordRepository;
import com.sateno_b.www.model.repository.GoogleAdsRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.json.XMLTokener.entity;

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

        GoogleAdsClient googleAdsClient = GoogleAdsClient.newBuilder()
                .setDeveloperToken(account.getDeveloperToken())
                .setLoginCustomerId(Long.parseLong(account.getLoginCustomerId()))
                .setCredentials(
                        com.google.auth.oauth2.UserCredentials.newBuilder()
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

            googleAdsServiceClient.search(request).iterateAll().forEach(row -> {

                String campaignName = row.getCampaign().getName();
                GoogleAdsCampaignName googleAdsCampaignName = googleAdsCampaignNameRepository.findByName(campaignName)
                        .orElseGet(() -> {
                            GoogleAdsCampaignName cn = new GoogleAdsCampaignName();
                            cn.setName(campaignName);
                            return googleAdsCampaignNameRepository.save(cn);
                        });

                GoogleAdsRecordEntity n = new GoogleAdsRecordEntity();
//                n.setId(String.valueOf(row.getCampaign().getId()));
//                n.setName(row.getCampaign().getName());
//                n.setS(row.getCampaign().getStatus().name());
                String dateStr = row.getSegments().getDate(); // "2026-06-03"
                int hour = row.getSegments().getHour();      // 15

// 1. Създаваме LocalDate от стринга
                LocalDate date = LocalDate.parse(dateStr);

// 2. Създаваме LocalTime от часа (минутите и секундите са 0)
                LocalTime time = LocalTime.of(hour, 0);

// 3. Комбинираме ги в LocalDateTime и конвертираме към Instant
// (Използваме системната зона, или UTC - препоръчително за Google Ads)
                Instant recordedAt = LocalDateTime.of(date, time)
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
                n.setRecordedAt(recordedAt);
                n.setClicks((int) row.getMetrics().getClicks());
                n.setImpressions((int) row.getMetrics().getImpressions());
                n.setSpend(row.getMetrics().getCostMicros() / 1_000_000.0);
                n.setCtr(row.getMetrics().getCtr());
                n.setCpc(row.getMetrics().getAverageCpc() / 1_000_000.0);
                n.setCpm(row.getMetrics().getAverageCpm() / 1_000_000.0);
                n.setAd(account);
                n.setCampaignName(googleAdsCampaignName);
                googleAdsRecordRepository.save(n);
            });
        }

    }
}
