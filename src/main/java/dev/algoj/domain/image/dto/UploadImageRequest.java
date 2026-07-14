package dev.algoj.domain.image.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadImageRequest(
        @NotBlank(message = "contentType은 필수입니다.")
        String contentType,

        @NotBlank(message = "base64Data는 필수입니다.")
        String base64Data
) {
}
