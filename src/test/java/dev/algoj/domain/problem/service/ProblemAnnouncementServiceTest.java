package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.entity.ProblemAnnouncement;
import dev.algoj.domain.problem.repository.ProblemAnnouncementRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProblemAnnouncementServiceTest {

    private static final String WEBHOOK = "https://discord.test/api/webhooks/1/abc";

    private final ProblemAnnouncementRepository repository = mock(ProblemAnnouncementRepository.class);

    private ProblemAnnouncementService service(RestClient.Builder builder, String webhook, String role) {
        return new ProblemAnnouncementService(builder, repository, webhook, role);
    }

    @Test
    void announce_postsContent_andOnlyAllowsTheConfiguredRoleToPing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(WEBHOOK + "?wait=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"content": "<@&123> hi @everyone",
                         "allowed_mentions": {"parse": [], "roles": ["123"]}}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service(builder, WEBHOOK, "123").announce("<@&123> hi @everyone", 2026, 10, 1, false);

        server.verify();
    }

    @Test
    void announce_withoutRole_allowsNoMentionsAtAll() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(WEBHOOK + "?wait=true"))
                .andExpect(content().json("""
                        {"allowed_mentions": {"parse": [], "roles": []}}
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service(builder, WEBHOOK, "").announce("hello", 2026, 10, 1, false);

        server.verify();
    }

    @Test
    void announce_recorded_savesTheChosenYearMonthWeek() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK + "?wait=true")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service(builder, WEBHOOK, "123").announce("hello", 2026, 10, 2, true);

        ArgumentCaptor<ProblemAnnouncement> saved = ArgumentCaptor.forClass(ProblemAnnouncement.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getYear()).isEqualTo(2026);
        assertThat(saved.getValue().getMonth()).isEqualTo(10);
        assertThat(saved.getValue().getWeekOfMonth()).isEqualTo(2);
        assertThat(saved.getValue().getContent()).isEqualTo("hello");
    }

    @Test
    void announce_testSend_isNotRecorded() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK + "?wait=true")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service(builder, WEBHOOK, "123").announce("hello", 2026, 10, 1, false);

        verify(repository, never()).save(any());
    }

    @Test
    void announce_whenDiscordRejects_throwsAnnounceFailed_andRecordsNothing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK + "?wait=true")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service(builder, WEBHOOK, "123").announce("hello", 2026, 10, 2, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANNOUNCE_FAILED);
        verify(repository, never()).save(any());
    }

    @Test
    void announce_withoutWebhook_isNotConfigured_andConfigSaysDisabled() {
        ProblemAnnouncementService service = service(RestClient.builder(), "", "123");

        assertThat(service.config().enabled()).isFalse();
        assertThatThrownBy(() -> service.announce("hello", 2026, 10, 2, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANNOUNCE_NOT_CONFIGURED);
    }

    @Test
    void config_exposesRoleId_butNeverTheWebhookUrl() {
        ProblemAnnouncementService service = service(RestClient.builder(), WEBHOOK, " 123 ");

        assertThat(service.config().enabled()).isTrue();
        assertThat(service.config().roleId()).isEqualTo("123");
    }

    @Test
    void suggest_isOnePastTheHighestWeekSentThatMonth() {
        when(repository.findMaxWeek(2026, 10)).thenReturn(0);
        when(repository.findMaxWeek(2026, 9)).thenReturn(3);
        ProblemAnnouncementService service = service(RestClient.builder(), WEBHOOK, "123");

        assertThat(service.suggest(2026, 10).weekOfMonth()).isEqualTo(1);
        assertThat(service.suggest(2026, 9).weekOfMonth()).isEqualTo(4);
    }
}
