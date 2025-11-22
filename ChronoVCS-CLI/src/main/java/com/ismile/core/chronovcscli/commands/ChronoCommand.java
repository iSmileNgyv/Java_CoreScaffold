package com.ismile.core.chronovcscli.commands;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "chronovcs",
        mixinStandardHelpOptions = true,     // adds --help, --version
        version = "1.0.0",
        description = "${cli.appName}",      // from application.yml
        subcommands = {
                InitCommand.class,           // we will create next
                CommitCommand.class,
                StatusCommand.class,
                AddCommand.class,
                RemoteHandshakeCommand.class,
                RemoteConfigCommand.class,
                LoginCommand.class
                // PushCommand.class,
                // PullCommand.class,
                // FetchCommand.class,
                // BranchCommand.class,
                // CheckoutCommand.class
        }
)
public class ChronoCommand {
    // intentionally empty — only serves as root command
}