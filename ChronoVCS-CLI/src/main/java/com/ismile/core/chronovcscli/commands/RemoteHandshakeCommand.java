package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.auth.CredentialsEntry;
import com.ismile.core.chronovcscli.auth.CredentialsService;
import com.ismile.core.chronovcscli.remote.RemoteConfig;
import com.ismile.core.chronovcscli.remote.RemoteConfigService;
import com.ismile.core.chronovcscli.remote.RemoteHandshakeService;
import com.ismile.core.chronovcscli.remote.dto.HandshakeResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.File;

@Component
@Command(name = "remote-handshake", description = "Test connection to remote ChronoVCS server")
@RequiredArgsConstructor
public class RemoteHandshakeCommand implements Runnable {

    private final CredentialsService credentialsService;
    private final RemoteConfigService remoteConfigService;
    private final RemoteHandshakeService remoteHandshakeService;

    @Override
    public void run() {
        try {
            File projectRoot = new File(".").getCanonicalFile();
            RemoteConfig remoteConfig = remoteConfigService.load(projectRoot);

            CredentialsEntry creds = credentialsService
                    .findForServer(remoteConfig.getBaseUrl())
                    .orElseThrow(() -> new IllegalStateException(
                            "No credentials configured for server " + remoteConfig.getBaseUrl()
                                    + ". Run: chronovcs login --server " + remoteConfig.getBaseUrl()
                                    + " --email <EMAIL>"
                    ));

            HandshakeResponseDto response = remoteHandshakeService.handshake(remoteConfig, creds);

            System.out.println("✅ Remote handshake OK");
            System.out.println("Server : " + remoteConfig.getBaseUrl());
            System.out.println("Repo   : " + response.getRepository().getRepoKey());
            System.out.println("Branch : " + response.getRepository().getDefaultBranch());
            System.out.println("Mode   : " + response.getRepository().getVersioningMode());
        } catch (Exception e) {
            System.out.println("Remote handshake failed: " + e.getMessage());
        }
    }
}