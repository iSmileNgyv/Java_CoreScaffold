package com.ismile.core.chronovcscli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

@Component
@Command(
        name = "remote-config",
        description = "Configure remote ChronoVCS server for this repository"
)
@RequiredArgsConstructor
public class RemoteConfigCommand implements Runnable {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Option(
            names = "--url",
            required = true,
            description = "ChronoVCS server base URL (e.g. http://localhost:8081)"
    )
    private String baseUrl;

    @Option(
            names = "--repo",
            required = true,
            description = "Repository key on server (e.g. my-repo)"
    )
    private String repoKey;

    @Override
    public void run() {
        try {
            File projectRoot = new File(".").getCanonicalFile();
            File vcsDir = new File(projectRoot, ".vcs");

            if (!vcsDir.isDirectory()) {
                System.out.println("Not a ChronoVCS repository (no .vcs directory found). Run 'chronovcs init' first.");
                return;
            }

            RemoteConfig config = new RemoteConfig();
            config.setBaseUrl(baseUrl);
            config.setRepoKey(repoKey);

            File remoteFile = new File(vcsDir, "remote.json");
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(remoteFile, config);

            System.out.println("Remote configuration saved to .vcs/remote.json");
            System.out.println("  baseUrl : " + baseUrl);
            System.out.println("  repoKey : " + repoKey);
            System.out.println("✅ Next step: run 'chronovcs login' (gələcəkdə yazacağıq).");

        } catch (Exception e) {
            System.out.println("Failed to save remote config: " + e.getMessage());
        }
    }
}