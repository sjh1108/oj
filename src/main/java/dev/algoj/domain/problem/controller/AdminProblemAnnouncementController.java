package dev.algoj.domain.problem.controller;

import dev.algoj.domain.problem.dto.ProblemAnnouncementConfigResponse;
import dev.algoj.domain.problem.dto.ProblemAnnouncementRequest;
import dev.algoj.domain.problem.service.ProblemAnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/problem-announcements")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProblemAnnouncementController {

    private final ProblemAnnouncementService problemAnnouncementService;

    @GetMapping("/config")
    public ResponseEntity<ProblemAnnouncementConfigResponse> config() {
        return ResponseEntity.ok(problemAnnouncementService.config());
    }

    @PostMapping
    public ResponseEntity<Void> announce(@Valid @RequestBody ProblemAnnouncementRequest request) {
        problemAnnouncementService.announce(request.content());
        return ResponseEntity.noContent().build();
    }
}
