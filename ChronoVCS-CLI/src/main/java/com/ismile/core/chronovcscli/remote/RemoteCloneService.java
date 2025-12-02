package com.ismile.core.chronovcscli.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.remote.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemoteCloneService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    public RefsResponseDto getRefs(RemoteConfig config, CredentialsEntry creds) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String url = baseUrl + "/api/repositories/" + config.getRepoKey() + "/refs";

            String basicToken = buildBasicAuth(creds);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + basicToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();

            if (status < 200 || status >= 300) {
                log.error("Get refs failed. Status: {}, Body: {}", status, body);
                throw new IllegalStateException("Get refs failed with status " + status + ": " + body);
            }

            return objectMapper.readValue(body, RefsResponseDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Get refs failed: " + e.getMessage(), e);
        }
    }

    public CommitSnapshotDto getCommit(RemoteConfig config, CredentialsEntry creds, String commitHash) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String url = baseUrl + "/api/repositories/" + config.getRepoKey() + "/commits/" + commitHash;

            String basicToken = buildBasicAuth(creds);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + basicToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();

            if (status < 200 || status >= 300) {
                log.error("Get commit failed. Status: {}, Body: {}", status, body);
                throw new IllegalStateException("Get commit failed with status " + status + ": " + body);
            }

            return objectMapper.readValue(body, CommitSnapshotDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Get commit failed: " + e.getMessage(), e);
        }
    }

    public CommitHistoryResponseDto getCommitHistory(RemoteConfig config,
                                                     CredentialsEntry creds,
                                                     String branch,
                                                     Integer limit) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            StringBuilder urlBuilder = new StringBuilder(baseUrl + "/api/repositories/" + config.getRepoKey() + "/commits");

            urlBuilder.append("?branch=").append(branch != null ? branch : "main");
            if (limit != null) {
                urlBuilder.append("&limit=").append(limit);
            }

            String basicToken = buildBasicAuth(creds);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .header("Authorization", "Basic " + basicToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();

            if (status < 200 || status >= 300) {
                log.error("Get commit history failed. Status: {}, Body: {}", status, body);
                throw new IllegalStateException("Get commit history failed with status " + status + ": " + body);
            }

            return objectMapper.readValue(body, CommitHistoryResponseDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Get commit history failed: " + e.getMessage(), e);
        }
    }

    public BatchObjectsResponseDto getBatchObjects(RemoteConfig config,
                                                   CredentialsEntry creds,
                                                   List<String> hashes) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String url = baseUrl + "/api/repositories/" + config.getRepoKey() + "/objects/batch";

            String basicToken = buildBasicAuth(creds);

            BatchObjectsRequestDto requestDto = new BatchObjectsRequestDto(hashes);
            String bodyJson = objectMapper.writeValueAsString(requestDto);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + basicToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();

            if (status < 200 || status >= 300) {
                log.error("Get batch objects failed. Status: {}, Body: {}", status, body);
                throw new IllegalStateException("Get batch objects failed with status " + status + ": " + body);
            }

            return objectMapper.readValue(body, BatchObjectsResponseDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Get batch objects failed: " + e.getMessage(), e);
        }
    }

    private String buildBasicAuth(CredentialsEntry entry) {
        String username;

        if (entry.getEmail() != null && !entry.getEmail().isBlank()) {
            username = entry.getEmail();
        } else if (entry.getUserUid() != null && !entry.getUserUid().isBlank()) {
            username = entry.getUserUid();
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
