package com.ismile.core.chronovcscli.commands;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.auth.CredentialsService;
import com.ismile.core.chronovcscli.auth.SelfResponseDto;
import com.ismile.core.chronovcscli.common.ApiErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.Console;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Scanner;

@Component
@Command(
        name = "login",
        description = "Login to a ChronoVCS server and save credentials locally"
)
@RequiredArgsConstructor
public class LoginCommand implements Runnable {

    @Option(
            names = {"--server"},
            required = true,
            description = "ChronoVCS server base URL (e.g. http://localhost:8081)"
    )
    private String server;

    @Option(
            names = {"--email"},
            required = true,
            description = "User email for Basic Auth"
    )
    private String email;

    private final CredentialsService credentialsService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    @Override
    public void run() {
        try {
            String normalizedServer = server.replaceAll("/$", "");

            String token = readTokenSecurely();

            String url = normalizedServer + "/api/auth/self";
            String basic = buildBasicAuth(email, token);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + basic)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();

            if (status < 200 || status >= 300) {
                handleErrorResponse(status, body);
                return;
            }

            // Parse successful response
            SelfResponseDto self = objectMapper.readValue(body, SelfResponseDto.class);

            CredentialsEntry entry = new CredentialsEntry(
                    normalizedServer,
                    self.getUserUid(),   // from server
                    self.getEmail(),     // confirmed email
                    token                // raw token
            );

            credentialsService.saveOrUpdate(entry);

            System.out.println("✅ Login successful!");
            System.out.println("  Server : " + normalizedServer);
            System.out.println("  User   : " + self.getEmail());
            System.out.println();
            System.out.println("Credentials saved locally. You can now:");
            System.out.println("  - configure remote: chronovcs remote-config --url " + normalizedServer + " --repo <REPO_KEY>");
            System.out.println("  - test connection : chronovcs remote-handshake");

        } catch (Exception e) {
            System.out.println("❌ Login failed: " + e.getMessage());
        }
    }

    private void handleErrorResponse(int status, String body) {
        String errorCode = null;
        String errorMessage = null;

        if (body != null && !body.isBlank()) {
            try {
                ApiErrorResponse apiError = objectMapper.readValue(body, ApiErrorResponse.class);
                errorCode = apiError.getErrorCode();
                errorMessage = apiError.getMessage();
            } catch (Exception ignored) {
                // If parsing fails, we will fall back to raw body
            }
        }

        System.out.println("❌ Login failed.");
        System.out.println("  Status : " + status);

        if (errorCode != null) {
            System.out.println("  Code   : " + errorCode);
        }
        if (errorMessage != null) {
            System.out.println("  Reason : " + errorMessage);
        } else if (body != null && !body.isBlank()) {
            System.out.println("  Detail : " + body);
        }

        System.out.println();
        System.out.println("Please check your email, token or server URL.");
    }

    private String readTokenSecurely() {
        Console console = System.console();
        if (console != null) {
            char[] pwd = console.readPassword("Token: ");
            return new String(pwd);
        } else {
            System.out.print("Token: ");
            Scanner scanner = new Scanner(System.in);
            return scanner.nextLine();
        }
    }

    private String buildBasicAuth(String username, String token) {
        String raw = username + ":" + token;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}