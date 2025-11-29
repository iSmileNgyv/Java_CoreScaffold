package com.ismile.core.chronovcscli.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class RemoteConfigService {

    private final ObjectMapper objectMapper;

    public RemoteConfig load(File projectRoot) throws IOException {
        File root = projectRoot.getAbsoluteFile();
        File vcsDir = new File(root, ".vcs");

        if (!vcsDir.isDirectory()) {
            throw new IllegalStateException("Not a ChronoVCS repository (no .vcs directory found).");
        }

        File remoteFile = new File(vcsDir, "remote.json");
        if (!remoteFile.isFile()) {
            throw new IllegalStateException("Remote config not found: .vcs/remote.json");
        }

        RemoteConfig config = objectMapper.readValue(remoteFile, RemoteConfig.class);

        if (isBlank(config.getBaseUrl()) || isBlank(config.getRepoKey())) {
            throw new IllegalStateException(
                    "Invalid .vcs/remote.json: baseUrl and repoKey are required."
            );
        }

        return config;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}