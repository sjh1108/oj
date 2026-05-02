package dev.algoj.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(min = 3, max = 50, message = "아이디는 3~50자여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "아이디는 영문/숫자/언더스코어만 가능합니다.")
        String username,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일은 최대 100자까지 가능합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 100, message = "비밀번호는 8~100자여야 합니다.")
        String password
) {
}
