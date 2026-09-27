package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.ProblemAnnouncementConfigResponse;
import dev.algoj.domain.problem.dto.ProblemAnnouncementRecordResponse;
import dev.algoj.domain.problem.dto.ProblemAnnouncementSuggestionResponse;
import dev.algoj.domain.problem.entity.ProblemAnnouncement;
import dev.algoj.domain.problem.repository.ProblemAnnouncementRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Posts the "선정 문제 알림" to a Discord channel webhook and keeps a record of
 * what was sent.
 *
 * A webhook rather than the bot: the bot's announce listener binds the box's
 * loopback, which the API container on algoj-net cannot reach. The webhook URL
 * is a secret — it only ever lives in .env on the server.
 *
 * The admin picks the month and week. The default week is "the nth study
 * actually held that month", not a calendar week: the study skips a week or two
 * a month and skipped weeks send no notice, so it is one past the highest week
 * already recorded for that month.
 */
@Slf4j
@Service
public class ProblemAnnouncementService {

    private final RestClient restClient;
    private final ProblemAnnouncementRepository repository;
    private final String webhookUrl;
    private final String roleId;

    public ProblemAnnouncementService(
            RestClient.Builder builder,
            ProblemAnnouncementRepository repository,
            @Value("${discord.problem-webhook-url:}") String webhookUrl,
            @Value("${discord.problem-role-id:}") String roleId) {
        this.restClient = builder.build();
        this.repository = repository;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.roleId = roleId == null ? "" : roleId.trim();
    }

    public ProblemAnnouncementConfigResponse config() {
        return new ProblemAnnouncementConfigResponse(!webhookUrl.isEmpty(), roleId.isEmpty() ? null : roleId);
    }

    @Transactional(readOnly = true)
    public ProblemAnnouncementSuggestionResponse suggest(int year, int month) {
        return new ProblemAnnouncementSuggestionResponse(
                year, month, repository.findMaxWeek(year, month) + 1);
    }

    @Transactional(readOnly = true)
    public List<ProblemAnnouncementRecordResponse> recent() {
        return repository.findTop10ByOrderByYearDescMonthDescWeekOfMonthDescIdDesc().stream()
                .map(ProblemAnnouncementRecordResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        ProblemAnnouncement announcement = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
        repository.delete(announcement);
    }

    /** Sends first and records only after Discord accepted it. */
    @Transactional
    public void announce(String content, int year, int month, int weekOfMonth, boolean record) {
        if (webhookUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.ANNOUNCE_NOT_CONFIGURED);
        }
        post(content);
        if (record) {
            repository.save(ProblemAnnouncement.of(year, month, weekOfMonth, content));
        }
    }

    private void post(String content) {
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
