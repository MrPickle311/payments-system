package com.example.payments.outbox.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}}")
    private String bootstrapServers;

    @Value("${spring.kafka.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${spring.kafka.ssl.trust-store-type:}")
    private String sslTrustStoreType;

    @Value("${spring.kafka.ssl.trust-store-location:}")
    private String sslTrustStoreLocation;

    @Value("${KAFKA_OAUTH_CLIENT_ID:}")
    private String oauthClientId;

    @Value("${KAFKA_OAUTH_CLIENT_SECRET:}")
    private String oauthClientSecret;

    @Value("${KAFKA_OAUTH_TOKEN_ENDPOINT:}")
    private String oauthTokenEndpoint;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        if (!sslTrustStoreType.isBlank()) {
            configProps.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslTrustStoreType);
        }
        if (!sslTrustStoreLocation.isBlank()) {
            configProps.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, sslTrustStoreLocation);
        }
        if (!oauthClientId.isBlank() && !oauthClientSecret.isBlank() && !oauthTokenEndpoint.isBlank()) {
            configProps.put(SaslConfigs.SASL_MECHANISM, "OAUTHBEARER");
            configProps.put(
                    SaslConfigs.SASL_JAAS_CONFIG,
                    String.format(
                            "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required "
                                    + "oauth.client.id=\"%s\" oauth.client.secret=\"%s\" oauth.token.endpoint.uri=\"%s\";",
                            oauthClientId, oauthClientSecret, oauthTokenEndpoint));
            configProps.put(
                    SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS,
                    "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");
        }
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
