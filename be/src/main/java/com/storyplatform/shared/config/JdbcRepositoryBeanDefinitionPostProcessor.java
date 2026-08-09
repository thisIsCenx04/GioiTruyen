package com.storyplatform.shared.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.stereotype.Component;

@Component
public class JdbcRepositoryBeanDefinitionPostProcessor
        implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private static final String JDBC_MAPPING_CONTEXT_REF = "org.springframework.data.jdbc.core.mapping.JdbcMappingContext";
    private static final String JDBC_MAPPING_CONTEXT_BEAN = "jdbcMappingContext";
    private static final String JDBC_DIALECT_REF = "org.springframework.data.relational.core.dialect.Dialect";
    private static final String JDBC_DIALECT_BEAN = "jdbcDialect";
    private static final String JDBC_CONVERTER_REF = "org.springframework.data.jdbc.core.convert.JdbcConverter";
    private static final String JDBC_CONVERTER_BEAN = "jdbcConverter";
    private static final String DATA_ACCESS_STRATEGY_REF = "org.springframework.data.jdbc.core.convert.DataAccessStrategy";
    private static final String DATA_ACCESS_STRATEGY_BEAN = "dataAccessStrategyBean";

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!registry.containsBeanDefinition(JDBC_MAPPING_CONTEXT_BEAN)) {
            registry.registerBeanDefinition(
                    JDBC_MAPPING_CONTEXT_BEAN,
                    new RootBeanDefinition(JdbcMappingContext.class)
            );
        }

        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
            Object mappingContext = beanDefinition.getPropertyValues().get("mappingContext");

            if (mappingContext instanceof RuntimeBeanReference reference
                    && JDBC_MAPPING_CONTEXT_REF.equals(reference.getBeanName())) {
                beanDefinition
                        .getPropertyValues()
                        .add("mappingContext", new RuntimeBeanReference(JDBC_MAPPING_CONTEXT_BEAN));
            }

            Object dialect = beanDefinition.getPropertyValues().get("dialect");

            if (dialect instanceof RuntimeBeanReference reference
                    && JDBC_DIALECT_REF.equals(reference.getBeanName())) {
                beanDefinition
                        .getPropertyValues()
                        .add("dialect", new RuntimeBeanReference(JDBC_DIALECT_BEAN));
            }

            Object converter = beanDefinition.getPropertyValues().get("converter");

            if (converter instanceof RuntimeBeanReference reference
                    && JDBC_CONVERTER_REF.equals(reference.getBeanName())) {
                beanDefinition
                        .getPropertyValues()
                        .add("converter", new RuntimeBeanReference(JDBC_CONVERTER_BEAN));
            }

            Object dataAccessStrategy = beanDefinition.getPropertyValues().get("dataAccessStrategy");

            if (dataAccessStrategy instanceof RuntimeBeanReference reference
                    && DATA_ACCESS_STRATEGY_REF.equals(reference.getBeanName())) {
                beanDefinition
                        .getPropertyValues()
                        .add("dataAccessStrategy", new RuntimeBeanReference(DATA_ACCESS_STRATEGY_BEAN));
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Bean definitions are normalized in postProcessBeanDefinitionRegistry.
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
