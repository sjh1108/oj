package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.ProblemAnnouncementConfigResponse;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Posts the "선정 문제 알림" to a Discord channel webhook.
 *
 * A webhook rather than the bot: the bot's announce listener binds the box's
 * loopback, which the API container on algoj-net cannot reach. The webhook URL
 * is a secret — it only ever lives in .env on the server.
 *
 * Which week was last announced is remembered in the admin's browser, not
 * here: it only seeds the default of a dropdown the admin can change anyway.
 */
@Slf4j
@Service
public class ProblemAnnouncementService {

    private final RestClient restClient;
    private final String webhookUrl;
    private final String roleId;

    public ProblemAnnouncementService(
            RestClient.Builder builder,
            @Value("${discord.problem-webhook-url:}") String webhookUrl,
            @Value("${discord.problem-role-id:}") String roleId) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.roleId = roleId == null ? "" : roleId.trim();
    }

    public ProblemAnnouncementConfigResponse config() {
        return new ProblemAnnouncementConfigResponse(!webhookUrl.isEmpty(), roleId.isEmpty() ? null : roleId);
    }

    public void announce(String content) {
        if (webhookUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.ANNOUNCE_NOT_CONFIGURED);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        // Mentions are parsed from the text, so an edited message could ping
        // @everyone or any role. Allow only the configured role.
        body.put("allowed_mentions", Map.of(
                "parse", List.of(),
                "roles", roleId.isEmpty() ? List.of() : List.of(roleId)));
        try {
            // wait=true makes Discord answer with the created message (or an
            // error) instead of a fire-and-forget 204.
            restClient.post()
                    .uri(webhookUrl + (webhookUrl.contains("?") ? "&" : "?") + "wait=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Problem announcement webhook failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ANNOUNCE_FAILED);
        }
    }
}
