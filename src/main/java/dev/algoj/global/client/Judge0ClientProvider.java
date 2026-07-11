package dev.algoj.global.client;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out Judge0 RestClients by read timeout. Judging uses wait=true, so the
 * HTTP wait must cover the problem's own time limit (up to 60s) — but a
 * RestClient's timeout is fixed at build time. Clients are cached per distinct
 * timeout; the configured default is the floor so a caller can never shorten it.
 */
public class Judge0ClientProvider {

    private final RestClient.Builder builder;
    private final String baseUrl;
    private final int connectTimeoutMs;
    private final int defaultReadTimeoutMs;
    private final Map<Integer, RestClient> byReadTimeout = new ConcurrentHashMap<>();

    public Judge0ClientProvider(RestClient.Builder builder,
                                String baseUrl,
                                int connectTimeoutMs,
                                int defaultReadTimeoutMs) {
        this.builder = builder;
        this.baseUrl = baseUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.defaultReadTimeoutMs = defaultReadTimeoutMs;
    }

    /** null → default read timeout; anything shorter than the default is raised to it. */
    public RestClient withReadTimeout(Integer readTimeoutMs) {
        int effective = readTimeoutMs != null
                ? Math.max(readTimeoutMs, defaultReadTimeoutMs)
                : defaultReadTimeoutMs;
        return byReadTimeout.computeIfAbsent(effective, ms -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
            factory.setReadTimeout(Duration.ofMillis(ms));
            return builder.clone()
                    .baseUrl(baseUrl)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .requestFactory(factory)
                    .build();
        });
    }
}
