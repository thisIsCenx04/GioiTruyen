package com.storyplatform;

import com.storyplatform.shared.config.JdbcRepositoryBeanDefinitionPostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.JdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.relational.RelationalManagedTypes;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.MySqlDialect;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.util.List;

@SpringBootApplication
@Import(JdbcRepositoryBeanDefinitionPostProcessor.class)
public class StoryPlatformApplication extends AbstractJdbcConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StoryPlatformApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(StoryPlatformApplication.class, args);
    }

    @Override
    @Bean("jdbcManagedTypes")
    public RelationalManagedTypes jdbcManagedTypes() throws ClassNotFoundException {
        return super.jdbcManagedTypes();
    }

    @Override
    @Bean("jdbcCustomConversions")
    public JdbcCustomConversions jdbcCustomConversions() {
        return super.jdbcCustomConversions();
    }

    @Bean("jdbcMappingContext")
    public JdbcMappingContext jdbcMappingContext() {
        return new JdbcMappingContext();
    }

    @Bean("jdbcConverter")
    public JdbcConverter jdbcConverter(
            JdbcMappingContext mappingContext,
            JdbcCustomConversions conversions
    ) {
        return new MappingJdbcConverter(
                mappingContext,
                (identifier, path) -> List.of(),
                conversions,
                JdbcTypeFactory.unsupported()
        );
    }

    @Override
    @Bean("dataAccessStrategyBean")
    public DataAccessStrategy dataAccessStrategyBean(
            NamedParameterJdbcOperations operations,
            JdbcConverter jdbcConverter,
            JdbcMappingContext context,
            Dialect dialect
    ) {
        return super.dataAccessStrategyBean(operations, jdbcConverter, context, dialect);
    }

    @Override
    @Bean("jdbcAggregateTemplate")
    public JdbcAggregateTemplate jdbcAggregateTemplate(
            ApplicationContext applicationContext,
            JdbcMappingContext mappingContext,
            JdbcConverter converter,
            DataAccessStrategy dataAccessStrategy
    ) {
        return super.jdbcAggregateTemplate(applicationContext, mappingContext, converter, dataAccessStrategy);
    }

    @Bean("jdbcDialect")
    public static Dialect jdbcDialect() {
        return new MySqlDialect(MySqlDialect.MYSQL_IDENTIFIER_PROCESSING);
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
