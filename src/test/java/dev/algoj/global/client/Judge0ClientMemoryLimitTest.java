package dev.algoj.global.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.algoj.global.client.dto.Judge0SubmissionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Judge0 rejects the whole submission with 422 when memory_limit exceeds its
 * MAX_MEMORY_LIMIT, so the client caps the value on the way out.
 */
class Judge0ClientMemoryLimitTest {

    private static final int MAX_KB = 512_000;
    private static final String ACCEPTED = """
            {"stdout":null,"stderr":null,"compile_output":null,"message":null,
             "time":"0.01","memory":1000,"token":"tok","status":{"id":3,"description":"Accepted"}}
            """;

    private record Fixture(Judge0Client client, MockRestServiceServer server) {}

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://judge0.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Judge0ClientProvider provider = mock(Judge0ClientProvider.class);
        given(provider.withReadTimeout(any())).willReturn(builder.build());
        return new Fixture(new Judge0Client(provider, new ObjectMapper(), MAX_KB), server);
    }

    private Judge0SubmissionRequest requestWithMemory(int memoryLimitKb) {
        return Judge0SubmissionRequest.of("print(1)", 71, "", "1", 2000, memoryLimitKb, 4096);
    }

    /** A 1GB problem must still be judged — at the box's ceiling, not refused. */
    @Test
    void memoryLimitAboveJudge0Max_isClamped() {
        Fixture f = fixture();
        f.server().expect(jsonPath("$.memory_limit").value(MAX_KB))
                .andRespond(withSuccess(ACCEPTED, MediaType.APPLICATION_JSON));

        f.client().submitAndWait(requestWithMemory(1_048_576));

        f.server().verify();
    }

    @Test
    void memoryLimitWithinJudge0Max_isSentUntouched() {
        Fixture f = fixture();
        f.server().expect(jsonPath("$.memory_limit").value(262_144))
                .andRespond(withSuccess(ACCEPTED, MediaType.APPLICATION_JSON));

        f.client().submitAndWait(requestWithMemory(262_144));

        f.server().verify();
    }
}
