package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.commit.CommitEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

@Command(
        name = "commit",
        description = "Create a new commit"
)
@Component
@RequiredArgsConstructor
public class CommitCommand implements Runnable {
    @Option(names = {"-m", "--message"}, description = "Commit message", required = true)
    private String message;
    private final CommitEngine commitEngine;

    @Override
    public void run() {
        try {
            File root = new File(System.getProperty("user.dir"));
            String id = commitEngine.commit(root, message);
            System.out.println("Created commit: " + id);
        } catch(Exception e) {
            System.err.println("Commit failed: " + e.getMessage());
        }
    }
}
