package com.storyplatform.analytics.infrastructure;

import com.storyplatform.analytics.application.TeamAnalyticsOperations;
import com.storyplatform.analytics.application.TeamAnalyticsService;
import com.storyplatform.analytics.application.port.TeamAnalyticsRepository;
import com.storyplatform.analytics.infrastructure.persistence.DisabledTeamAnalyticsRepository;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AnalyticsConfiguration {

    @Bean
    TeamAnalyticsRepository teamAnalyticsRepository() {
        return new DisabledTeamAnalyticsRepository();
    }

    @Bean
    TeamAnalyticsOperations teamAnalyticsOperations(
            TeamPermissionAuthorizer permissions,
            TeamAnalyticsRepository repository
    ) {
        return new TeamAnalyticsService(
                permissions,
                repository,
                Clock.systemUTC()
        );
    }
}
