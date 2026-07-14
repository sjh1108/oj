package dev.algoj.domain.image.controller;

import dev.algoj.domain.image.dto.UploadImageRequest;
import dev.algoj.domain.image.dto.UploadImageResponse;
import dev.algoj.domain.image.service.ImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ImageController {

    private final ImageService imageService;

    @PostMapping
    public ResponseEntity<UploadImageResponse> upload(@Valid @RequestBody UploadImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imageService.upload(request));
    }
}
