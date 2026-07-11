package dev.algoj.global.config;

import dev.algoj.global.client.Judge0ClientProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class Judge0Config {

    @Bean
    public Judge0ClientProvider judge0ClientProvider(
            RestClient.Builder builder,
            @Value("${judge0.url}") String baseUrl,
            @Value("${judge0.connect-timeout-ms}") int connectMs,
            @Value("${judge0.read-timeout-ms}") int readMs) {
        return new Judge0ClientProvider(builder, baseUrl, connectMs, readMs);
    }
}
