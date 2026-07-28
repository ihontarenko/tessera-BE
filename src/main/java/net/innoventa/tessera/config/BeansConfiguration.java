package net.innoventa.tessera.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.UUID;
import java.util.function.Supplier;

@Configuration
public class BeansConfiguration {

    @Bean("idGenerator")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Supplier<String> idGenerator() {
        return () -> UUID.randomUUID().toString();
    }

}
