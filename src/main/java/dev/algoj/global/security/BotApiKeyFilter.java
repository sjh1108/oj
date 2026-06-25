package dev.algoj.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.algoj.global.exception.ErrorCode;
import dev.algoj.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Guards /api/internal/** with a shared bot API key (header X-Bot-Api-Key).
 * Fails closed: if no key is configured, all internal requests are rejected.
 */
@Component
public class BotApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Bot-Api-Key";
    private static final String INTERNAL_PREFIX = "/api/internal/";

    private final ObjectMapper objectMapper;
    private final String botApiKey;

    public BotApiKeyFilter(ObjectMapper objectMapper,
                           @Value("${bot.api-key:}") String botApiKey) {
        this.objectMapper = objectMapper;
        this.botApiKey = botApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith(INTERNAL_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (!StringUtils.hasText(botApiKey) || !botApiKey.equals(provided)) {
            response.setStatus(ErrorCode.INVALID_BOT_KEY.getHttpStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    objectMapper.writeValueAsString(ErrorResponse.of(ErrorCode.INVALID_BOT_KEY)));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
