package com.ismile.core.chronovcscli;

import com.ismile.core.chronovcscli.commands.ChronoCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

@SpringBootApplication
public class ChronoVcsCliApplication implements CommandLineRunner {

    private final ChronoCommand chronoCommand;
    private final IFactory factory;

    public ChronoVcsCliApplication(ChronoCommand chronoCommand, IFactory factory) {
        this.chronoCommand = chronoCommand;
        this.factory = factory;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(ChronoVcsCliApplication.class, args)));
    }

    @Override
    public void run(String... args) {
        new CommandLine(chronoCommand, factory).execute(args);
    }
}
