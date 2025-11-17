package com.ismile.core.chronovcscli.commands;

import com.ismile.core.chronovcscli.core.VcsDirectoryManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "init",
        description = "Initialize a new ChronoVCS repository in the current directory"
)
public class InitCommand implements Runnable {

    @Override
    public void run() {
        try {
            VcsDirectoryManager.initRepository();
            System.out.println("Initialized empty ChronoVCS repository in .vcs/");
        } catch (Exception e) {
            System.out.println("Error initializing ChronoVCS repository: " + e.getMessage());
        }
    }
}