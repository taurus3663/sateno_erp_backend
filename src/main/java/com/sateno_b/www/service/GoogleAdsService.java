package com.sateno_b.www.service;

import com.google.ads.googleads.lib.GoogleAdsClient;
//import com.google.ads.googleads.v19.services.GoogleAdsServiceClient;
//import com.google.ads.googleads.v19.services.SearchGoogleAdsRequest;
import com.google.ads.googleads.v24.services.ListAccessibleCustomersRequest;
import com.google.ads.googleads.v24.services.CustomerServiceClient;
import com.google.ads.googleads.v24.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v24.services.ListAccessibleCustomersResponse;
import com.google.ads.googleads.v24.services.SearchGoogleAdsRequest;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAdsService {

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
            String query = "SELECT campaign.id, campaign.name FROM campaign WHERE campaign.status = 'ENABLED'";

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

    static final String CLIENT_ID = "797055843332-fg61agga3ej70b3b74cn8s8veker1n57.apps.googleusercontent.com";
    static final String CLIENT_SECRET = "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";
    static final String SCOPE = "https://www.googleapis.com/auth/adwords";

    public void gen() throws IOException {
        HttpTransport httpTransport = new NetHttpTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(CLIENT_ID);
        details.setClientSecret(CLIENT_SECRET);

        GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
        clientSecrets.setInstalled(details);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, Arrays.asList(SCOPE))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        System.out.println("REFRESH TOKEN: " + credential.getRefreshToken());
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

}
