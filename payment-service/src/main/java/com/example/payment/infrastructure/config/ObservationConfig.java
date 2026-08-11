package com.example.payment.infrastructure.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.semconv.ServiceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfig {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public Resource otelResource(@Value("${spring.application.name}") String applicationName) {
        return Resource.getDefault().merge(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, applicationName)));
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider, ContextPropagators propagators) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(propagators)
                .build();
    }
}
