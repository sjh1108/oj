package dev.algoj.global.config;

import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "algoj.seed-admin", havingValue = "true")
@RequiredArgsConstructor
public class AdminSeeder {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_EMAIL = "admin@algoj.dev";
    private static final String ADMIN_DEFAULT_PASSWORD = "admin1234";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner seedAdmin() {
        return args -> {
            if (userRepository.existsByUsername(ADMIN_USERNAME)) {
                return;
            }
            User admin = User.builder()
                    .username(ADMIN_USERNAME)
                    .email(ADMIN_EMAIL)
                    .password(passwordEncoder.encode(ADMIN_DEFAULT_PASSWORD))
                    .role(User.Role.ADMIN)
                    .build();
            userRepository.save(admin);
            log.warn("Seeded default admin user '{}'. Change password before production.", ADMIN_USERNAME);
        };
    }
}
