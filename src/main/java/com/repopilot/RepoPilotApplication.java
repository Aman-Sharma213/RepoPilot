package com.repopilot;

import com.repopilot.cli.RootCommand;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Spring Boot entry point. Spring owns the application lifecycle; Picocli owns argument parsing.
 * The CLI logic itself lives in {@link RootCommand} and is intentionally usable without Spring
 * so that command tests stay fast and deterministic.
 */
@SpringBootApplication
public class RepoPilotApplication implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private int exitCode;

    public RepoPilotApplication(IFactory factory) {
        this.factory = factory;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(new SpringApplicationBuilder(RepoPilotApplication.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .bannerMode(org.springframework.boot.Banner.Mode.OFF)
                .run(args)));
    }

    @Override
    public void run(String... args) {
        this.exitCode = new CommandLine(new RootCommand(), factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
