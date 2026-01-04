package com.ismile.core.chronovcscli.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.remote.dto.PushRequestDto;
import com.ismile.core.chronovcscli.remote.dto.PushResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemotePushService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    public PushResultDto push(RemoteConfig config,
                              CredentialsEntry creds,
                              PushRequestDto requestDto) {

        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String url = baseUrl + "/api/repositories/" + config.getRepoKey() + "/push";

            String basicToken = buildBasicAuth(creds);

            String bodyJson = objectMapper.writeValueAsString(requestDto);
            byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + basicToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();

            if (status < 200 || status >= 300) {
                log.error("Remote push failed. Status: {}, Body: {}", status, body);
                throw new IllegalStateException(
                        "Remote push failed with status " + status + ": " + body
                );
            }

            return objectMapper.readValue(body, PushResultDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Remote push failed: " + e.getMessage(), e);
        }
    }

    private String buildBasicAuth(CredentialsEntry entry) {
        String username;

        if (entry.getEmail() != null && !entry.getEmail().isBlank()) {
            username = entry.getEmail();         // artıq email ilə auth edirik
        } else if (entry.getUserUid() != null && !entry.getUserUid().isBlank()) {
            username = entry.getUserUid();       // fallback
        } else {
            throw new IllegalStateException("No email or userUid configured for credentials");
        }

        if (entry.getToken() == null || entry.getToken().isBlank()) {
            throw new IllegalStateException("No token configured for credentials");
        }

        String raw = username + ":" + entry.getToken();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
