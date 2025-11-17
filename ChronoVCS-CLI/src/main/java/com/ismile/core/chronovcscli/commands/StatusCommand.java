package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.status.StatusEngine;
import com.ismile.core.chronovcscli.core.status.StatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.File;

@Command(
        name = "status",
        description = "Show changes in working directory"
)
@Component
@RequiredArgsConstructor
public class StatusCommand implements Runnable{
    private final StatusEngine statusEngine;
    @Override
    public void run() {
        try {
            File root = new File(System.getProperty("user.dir"));
            StatusResult result = statusEngine.getStatus(root);
            System.out.println("Untracked:");
            result.getUntracked().forEach(f -> System.out.println(" " + f));

            System.out.println("\nModified:");
            result.getModified().forEach(f -> System.out.println(" " + f));

            System.out.println("\nDeleted:");
            result.getDeleted().forEach(f -> System.out.println(" " + f));
        } catch(Exception e) {
            System.err.println("Status failed: " + e.getMessage());
        }
    }
}
