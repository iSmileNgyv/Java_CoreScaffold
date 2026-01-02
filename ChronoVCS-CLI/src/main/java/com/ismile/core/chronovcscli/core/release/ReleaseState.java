package com.ismile.core.chronovcscli.core.release;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

public class ReleaseState {
    private final String version;
    private final String commitId;

    public ReleaseState(String version, String commitId) {
        this.version = version;
        this.commitId = commitId;
    }

    public String getVersion() {
        return version;
    }

    public String getCommitId() {
        return commitId;
    }

    public static Optional<ReleaseState> load(File projectRoot) {
        try {
            File releaseFile = new File(projectRoot, ".vcs/RELEASE");
            if (!releaseFile.exists()) {
                return Optional.empty();
            }

            List<String> lines = Files.readAllLines(releaseFile.toPath());
            String version = null;
            String commit = null;

            for (String line : lines) {
                if (line.startsWith("version=")) {
                    version = line.substring("version=".length()).trim();
                } else if (line.startsWith("commit=")) {
                    commit = line.substring("commit=".length()).trim();
                }
            }

            if (version == null || version.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new ReleaseState(version, commit));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static void save(File projectRoot, ReleaseState state) throws Exception {
        File releaseFile = new File(projectRoot, ".vcs/RELEASE");
        releaseFile.getParentFile().mkdirs();

        StringBuilder content = new StringBuilder();
        content.append("version=").append(state.getVersion()).append("\n");
        if (state.getCommitId() != null && !state.getCommitId().isBlank()) {
            content.append("commit=").append(state.getCommitId()).append("\n");
        }

        Files.writeString(releaseFile.toPath(), content.toString());
    }
}
