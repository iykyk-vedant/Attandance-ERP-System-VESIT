package com.vesit.openattend.service.sync;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class SheetsClient {

    private final Sheets sheetsService;
    private final boolean isMockMode;

    public SheetsClient(@Value("${openattend.google.service-account-json:}") String credentialsJson) {
        Sheets service = null;
        boolean mock = true;

        if (credentialsJson != null && !credentialsJson.trim().isEmpty()) {
            try {
                String rawJson = credentialsJson.trim();
                // If Base64 encoded, decode first
                if (!rawJson.startsWith("{")) {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(rawJson);
                        rawJson = new String(decoded, StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                if (rawJson.startsWith("{")) {
                    GoogleCredentials credentials = GoogleCredentials.fromStream(
                            new ByteArrayInputStream(rawJson.getBytes(StandardCharsets.UTF_8))
                    ).createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY));

                    service = new Sheets.Builder(
                            new NetHttpTransport(),
                            GsonFactory.getDefaultInstance(),
                            new HttpCredentialsAdapter(credentials)
                    ).setApplicationName("OpenAttend-VESIT").build();

                    mock = false;
                    log.info("SheetsClient initialized successfully with read-only Google credentials.");
                }
            } catch (Exception e) {
                log.warn("Failed to initialize Google Sheets Client from credentials. Falling back to mock mode: {}", e.getMessage());
            }
        }

        this.sheetsService = service;
        this.isMockMode = mock;
        if (this.isMockMode) {
            log.info("SheetsClient operating in MOCK / TEST mode (no live Google Sheets API calls).");
        }
    }

    public List<List<Object>> getRangeValues(String spreadsheetId, String range) throws IOException {
        if (isMockMode || sheetsService == null) {
            return Collections.emptyList();
        }

        int maxRetries = 3;
        long backoffMs = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ValueRange response = sheetsService.spreadsheets().values()
                        .get(spreadsheetId, range)
                        .setValueRenderOption("FORMATTED_VALUE")
                        .execute();
                List<List<Object>> values = response.getValues();
                return values != null ? values : Collections.emptyList();
            } catch (IOException e) {
                if (attempt == maxRetries) {
                    throw e;
                }
                log.warn("Google Sheets API request failed (attempt {}/{}). Retrying in {}ms: {}", attempt, maxRetries, backoffMs, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                backoffMs *= 2;
            }
        }

        return Collections.emptyList();
    }

    public boolean isMockMode() {
        return isMockMode;
    }
}
