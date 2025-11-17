package com.ismile.core.chronovcscli.core;

import java.io.File;
import java.nio.file.Files;

public class VcsDirectoryManager {

    public static void initRepository() throws Exception {

        File vcsDir = new File(".vcs");

        if (vcsDir.exists()) {
            throw new Exception("A ChronoVCS repository already exists in this folder.");
        }

        // Create root .vcs directory
        new File(".vcs").mkdir();

        // Create required subdirectories
        new File(".vcs/objects").mkdirs();
        new File(".vcs/commits").mkdirs();
        new File(".vcs/refs/heads").mkdirs();

        // Create default branch reference: main
        File mainRef = new File(".vcs/refs/heads/main");
        mainRef.createNewFile(); // empty

        // Create HEAD pointer
        File head = new File(".vcs/HEAD");
        head.createNewFile();
        Files.writeString(head.toPath(), "ref: refs/heads/main");

        // Create repo config file
        File config = new File(".vcs/config");
        config.createNewFile();
        Files.writeString(config.toPath(), """
                [repository]
                default_branch=main
                versioning_mode=project
                """);
    }
}