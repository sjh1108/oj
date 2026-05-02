package dev.algoj.global.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.algoj.global.client.dto.Judge0SubmissionRequest;
import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class Judge0Client {

    private final RestClient judge0RestClient;
    private final ObjectMapper objectMapper;

    public Judge0SubmissionResponse submitAndWait(Judge0SubmissionRequest plain) {
        try {
            Judge0SubmissionRequest encoded = encodeRequest(plain);
            String json = objectMapper.writeValueAsString(encoded);
            log.debug("Judge0 request body: {}", json);

            String rawBody = judge0RestClient.post()
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
                p.memoryLimit()
        );
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
