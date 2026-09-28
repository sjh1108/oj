package dev.algoj.global.config;

import dev.algoj.domain.image.controller.ImageController;
import dev.algoj.domain.image.service.ImageService;
import dev.algoj.global.security.CustomUserDetailsService;
import dev.algoj.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Locally stored statement images are served publicly at /images/** — and only rendered, never run. */
// 컨트롤러 하나만 올린다 — 비워 두면 모든 컨트롤러와 그 서비스가 필요해진다.
@WebMvcTest(controllers = ImageController.class)
@Import({SecurityConfig.class, ImageResourceConfig.class})
class ImageResourceConfigTest {

    @TempDir
    static Path dir;

    @MockitoBean
    ImageService imageService;
    @MockitoBean
    JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    CustomUserDetailsService userDetailsService;

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void imageDir(DynamicPropertyRegistry registry) {
        registry.add("app.images.dir", () -> dir.toString());
    }

    @BeforeAll
    static void writeImages() throws Exception {
        Files.createDirectories(dir.resolve("problems"));
        Files.write(dir.resolve("problems/a.png"), new byte[]{1, 2, 3});
        Files.writeString(dir.resolve("problems/b.svg"),
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>");
    }

    @Test
    void servesImageWithoutLogin_cachedForever() throws Exception {
        mvc.perform(get("/images/problems/a.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void svgIsSandboxed_soItsScriptsNeverRun() throws Exception {
        mvc.perform(get("/images/problems/b.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void missingImageIs404() throws Exception {
        mvc.perform(get("/images/problems/none.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotEscapeTheImageDirectory() throws Exception {
        mvc.perform(get("/images/../../etc/passwd"))
                .andExpect(status().is4xxClientError());
    }
}
