package com.storyplatform.reading.infrastructure;

import com.storyplatform.reading.application.ReadingProgressOperations;
import com.storyplatform.reading.application.ReadingProgressService;
import com.storyplatform.reading.application.port.ReadingProgressRepository;
import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingProgressRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ReadingConfiguration {

    @Bean
    ReadingProgressRepository readingProgressRepository(
            MongoTemplate mongo
    ) {
        return new MongoReadingProgressRepository(mongo);
    }

    @Bean
    ReadingProgressOperations readingProgressOperations(
            ReadingProgressRepository repository
    ) {
        return new ReadingProgressService(
                repository,
                Clock.systemUTC()
        );
    }
}
