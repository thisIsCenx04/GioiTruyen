package com.storyplatform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class StoryPlatformApplication {

    private static final Logger log = LoggerFactory.getLogger(StoryPlatformApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(StoryPlatformApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "/api/v1");
        String host = env.getProperty("server.address", "0.0.0.0");
        String activeProfile = String.join(", ", env.getActiveProfiles());

        String banner = """

==================================================================================
  >>> BACKEND SPRING BOOT HAS STARTED SUCCESSFULLY! <<<
  
  * Active Profile : %s
  * API Base URL   : http://%s:%s%s
  * Health Check   : http://localhost:%s%s/actuator/health
  * Status         : 100%% ONLINE & READY FOR REQUESTS
==================================================================================
""".formatted(
                activeProfile.isEmpty() ? "default" : activeProfile,
                host.equals("0.0.0.0") ? "localhost" : host,
                port,
                contextPath,
                port,
                contextPath
        );

        System.out.println(banner);
        log.info("Backend Spring Boot started successfully on port {} with context path {}", port, contextPath);
    }
}
