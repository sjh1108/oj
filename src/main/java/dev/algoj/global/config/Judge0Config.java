package dev.algoj.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class Judge0Config {

    @Bean
    public RestClient judge0RestClient(
            RestClient.Builder builder,
            @Value("${judge0.url}") String baseUrl,
            @Value("${judge0.connect-timeout-ms}") int connectMs,
            @Value("${judge0.read-timeout-ms}") int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectMs));
        factory.setReadTimeout(Duration.ofMillis(readMs));
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }
}
