package com.acme.order.common.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Money and quantities are integers; silently truncating fractional JSON numbers is unsafe. */
@Configuration
public class JsonRequestConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictIntegers() {
        return builder -> builder.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
