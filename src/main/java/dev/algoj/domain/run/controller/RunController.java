package dev.algoj.domain.run.controller;

import dev.algoj.domain.run.dto.RunRequest;
import dev.algoj.domain.run.dto.RunResponse;
import dev.algoj.domain.run.service.RunService;
import dev.algoj.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/run")
@RequiredArgsConstructor
public class RunController {

    private final RunService runService;

    @PostMapping
    public ResponseEntity<RunResponse> run(
            @Valid @RequestBody RunRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(runService.run(request, principal));
    }
}
