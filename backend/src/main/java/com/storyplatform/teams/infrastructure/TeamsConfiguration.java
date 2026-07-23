package com.storyplatform.teams.infrastructure;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.teams.application.ProfileService;
import com.storyplatform.teams.application.port.UserProfileRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class TeamsConfiguration {

    @Bean
    ProfileService profileService(
            IdentityUserDirectory identities,
            UserProfileRepository profiles
    ) {
        return new ProfileService(
                identities,
                profiles,
                Clock.systemUTC()
        );
    }
}
