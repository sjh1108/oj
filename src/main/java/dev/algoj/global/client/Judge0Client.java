package dev.algoj.global.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.algoj.global.client.dto.Judge0SubmissionRequest;
import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class Judge0Client {

    private final Judge0ClientProvider judge0Clients;
    private final ObjectMapper objectMapper;
    /** Judge0's own MAX_MEMORY_LIMIT — anything above it is refused with 422. */
    private final int maxMemoryLimitKb;

    public Judge0Client(Judge0ClientProvider judge0Clients,
                        ObjectMapper objectMapper,
                        @Value("${judge0.max-memory-limit-kb}") int maxMemoryLimitKb) {
        this.judge0Clients = judge0Clients;
        this.objectMapper = objectMapper;
        this.maxMemoryLimitKb = maxMemoryLimitKb;
    }

    public Judge0SubmissionResponse submitAndWait(Judge0SubmissionRequest plain) {
        return submitAndWait(plain, null);
    }

    /**
     * @param readTimeoutMs how long to hold the wait=true connection — pass the
     *                      problem's time limit plus margin so a legally slow run
     *                      isn't cut off; null uses the configured default.
     */
    public Judge0SubmissionResponse submitAndWait(Judge0SubmissionRequest plain, Integer readTimeoutMs) {
        try {
            Judge0SubmissionRequest encoded = encodeRequest(plain);
            String json = objectMapper.writeValueAsString(encoded);
            log.debug("Judge0 request body: {}", json);

            String rawBody = judge0Clients.withReadTimeout(readTimeoutMs).post()
                    .uri(uri -> uri.path("/submissions")
                            .queryParam("base64_encoded", "true")
                            .queryParam("wait", "true")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);

            log.debug("Judge0 response body: {}", rawBody);
            Judge0SubmissionResponse encResp = objectMapper.readValue(rawBody, Judge0SubmissionResponse.class);
            return decodeResponse(encResp);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.JUDGE0_ERROR, "serialize/parse failed: " + e.getMessage());
        } catch (RestClientException e) {
            log.error("Judge0 call failed", e);
            throw new BusinessException(ErrorCode.JUDGE0_ERROR, e.getMessage());
        }
    }

    private Judge0SubmissionRequest encodeRequest(Judge0SubmissionRequest p) {
        return new Judge0SubmissionRequest(
                b64Encode(p.sourceCode()),
                p.languageId(),
                b64Encode(p.stdin()),
                b64Encode(p.expectedOutput()),
                p.cpuTimeLimit(),
                clampMemoryLimit(p.memoryLimit()),
                p.maxFileSize()
        );
    }

    /**
     * A problem may declare more memory than the Judge0 box allows (registration
     * permits up to 1GB, the box caps at MAX_MEMORY_LIMIT). Sending it verbatim
     * gets the whole submission refused with
     * {@code 422 {"memory_limit":["must be less than or equal to N"]}} — judging
     * at the box's ceiling is far better than failing the run outright.
     */
    private Integer clampMemoryLimit(Integer requestedKb) {
        if (requestedKb == null || requestedKb <= maxMemoryLimitKb) return requestedKb;
        log.warn("memory_limit {}KB exceeds Judge0 max {}KB — clamping (raise MAX_MEMORY_LIMIT "
                + "on the Judge0 box and judge0.max-memory-limit-kb together to honor it)",
                requestedKb, maxMemoryLimitKb);
        return maxMemoryLimitKb;
    }

    private Judge0SubmissionResponse decodeResponse(Judge0SubmissionResponse r) {
        return new Judge0SubmissionResponse(
                b64Decode(r.stdout()),
                b64Decode(r.stderr()),
                b64Decode(r.compileOutput()),
                b64Decode(r.message()),
                r.time(),
                r.memory(),
                r.token(),
                r.status()
        );
    }

    /** Cheap liveness probe for monitoring — true when Judge0 answers /languages. */
    public boolean isUp() {
        try {
            judge0Clients.withReadTimeout(null).get()
                    .uri("/languages")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Judge0 liveness probe failed: {}", e.getMessage());
            return false;
        }
    }

    private static String b64Encode(String s) {
        if (s == null) return null;
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64Decode(String s) {
        if (s == null || s.isEmpty()) return s;
        try {
            return new String(Base64.getMimeDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }
}
