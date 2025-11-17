package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.add.AddEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;

@CommandLine.Command(
        name = "add",
        description = "Add files to the staging area"
)
@Component
@RequiredArgsConstructor
public class AddCommand implements Runnable {
    @CommandLine.Parameters(paramLabel = "path", description = "File or directory to add")
    private String path;
    private final AddEngine addEngine;

    @Override
    public void run() {
        try {
            File root = new File(System.getProperty("user.dir"));
            addEngine.add(root, path);
            System.out.println("Added: " + path);
        } catch(Exception e) {
            System.err.println("Add failed: " + e.getMessage());
        }
    }
}
