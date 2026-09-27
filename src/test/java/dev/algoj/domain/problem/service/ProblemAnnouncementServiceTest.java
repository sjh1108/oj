package dev.algoj.domain.problem.service;

import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProblemAnnouncementServiceTest {

    private static final String WEBHOOK = "https://discord.test/api/webhooks/1/abc";

    @Test
    void announce_postsContent_andOnlyAllowsTheConfiguredRoleToPing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProblemAnnouncementService service = new ProblemAnnouncementService(builder, WEBHOOK, "123");

        server.expect(requestTo(WEBHOOK + "?wait=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"content": "<@&123> hi @everyone",
                         "allowed_mentions": {"parse": [], "roles": ["123"]}}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.announce("<@&123> hi @everyone");

        server.verify();
    }

    @Test
    void announce_withoutRole_allowsNoMentionsAtAll() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProblemAnnouncementService service = new ProblemAnnouncementService(builder, WEBHOOK, "");

        server.expect(requestTo(WEBHOOK + "?wait=true"))
                .andExpect(content().json("""
                        {"allowed_mentions": {"parse": [], "roles": []}}
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.announce("hello");

        server.verify();
    }

    @Test
    void announce_whenDiscordRejects_throwsAnnounceFailed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProblemAnnouncementService service = new ProblemAnnouncementService(builder, WEBHOOK, "123");

        server.expect(requestTo(WEBHOOK + "?wait=true")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.announce("hello"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANNOUNCE_FAILED);
    }

    @Test
    void announce_withoutWebhook_isNotConfigured_andConfigSaysDisabled() {
        ProblemAnnouncementService service = new ProblemAnnouncementService(RestClient.builder(), "", "123");

        assertThat(service.config().enabled()).isFalse();
        assertThatThrownBy(() -> service.announce("hello"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANNOUNCE_NOT_CONFIGURED);
    }

    @Test
    void config_exposesRoleId_butNeverTheWebhookUrl() {
        ProblemAnnouncementService service = new ProblemAnnouncementService(RestClient.builder(), WEBHOOK, " 123 ");

        assertThat(service.config().enabled()).isTrue();
        assertThat(service.config().roleId()).isEqualTo("123");
    }
}
