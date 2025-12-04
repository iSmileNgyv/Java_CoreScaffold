package com.ismile.argusomnicli;

import com.ismile.argusomnicli.cli.ArgusCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

/**
 * Main Spring Boot application for ArgusOmni CLI.
 * Integrates PicoCLI with Spring Boot.
 */
@SpringBootApplication
public class ArgusOmniApplication implements CommandLineRunner {

    private final ArgusCommand argusCommand;
    private final CommandLine.IFactory factory;

    public ArgusOmniApplication(ArgusCommand argusCommand, CommandLine.IFactory factory) {
        this.argusCommand = argusCommand;
        this.factory = factory;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(ArgusOmniApplication.class, args)));
    }

    @Override
    public void run(String... args) throws Exception {
        CommandLine cmd = new CommandLine(argusCommand, factory);
        cmd.execute(args);
    }
}
