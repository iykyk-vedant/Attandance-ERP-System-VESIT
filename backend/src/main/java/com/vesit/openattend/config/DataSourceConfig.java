package com.vesit.openattend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class DataSourceConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof HikariDataSource hikariDataSource) {
            Properties dsProps = hikariDataSource.getDataSourceProperties();
            if (dsProps == null) {
                dsProps = new Properties();
            }
            dsProps.setProperty("prepareThreshold", "0");
            dsProps.setProperty("preparedStatementCacheQueries", "0");
            dsProps.setProperty("cleanupSavepoints", "true");
            hikariDataSource.setDataSourceProperties(dsProps);
        }
        return bean;
    }
}
