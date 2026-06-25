package dev.algoj.domain.user.controller;

import dev.algoj.domain.user.dto.AdminResetPasswordRequest;
import dev.algoj.domain.user.dto.AdminResetPasswordResponse;
import dev.algoj.domain.user.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/reset-password")
    public ResponseEntity<AdminResetPasswordResponse> resetPassword(
            @Valid @RequestBody AdminResetPasswordRequest request) {
        return ResponseEntity.ok(adminUserService.resetPassword(request));
    }
}
