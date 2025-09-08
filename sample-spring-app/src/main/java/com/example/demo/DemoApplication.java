package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sample Spring Boot application used to demonstrate and test the jlib-inspector
 * agent attached to a running Java process. This app doesn't expose HTTP
 * endpoints; it simply starts up and logs the command-line arguments so that
 * the agent can observe class loading and JAR usage.
 */
@SpringBootApplication
public class DemoApplication implements CommandLineRunner {

    private static Logger LOG = LoggerFactory.getLogger(DemoApplication.class);

    public static void main(String[] args) {
        LOG.info("STARTING THE APPLICATION");
        SpringApplication.run(DemoApplication.class, args);
        LOG.info("APPLICATION FINISHED");
    }

    @Override
    public void run(String... args) {
        LOG.info("EXECUTING : command line runner");

        for (int i = 0; i < args.length; ++i) {
            LOG.info("args[{}]: {}", i, args[i]);
        }
    }
}
