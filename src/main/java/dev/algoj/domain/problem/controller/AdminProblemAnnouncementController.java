package dev.algoj.domain.problem.controller;

import dev.algoj.domain.problem.dto.ProblemAnnouncementConfigResponse;
import dev.algoj.domain.problem.dto.ProblemAnnouncementRecordResponse;
import dev.algoj.domain.problem.dto.ProblemAnnouncementRequest;
import dev.algoj.domain.problem.dto.ProblemAnnouncementSuggestionResponse;
import dev.algoj.domain.problem.service.ProblemAnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** Default week for a month: one past the highest week already announced. */
    @GetMapping("/suggestion")
    public ResponseEntity<ProblemAnnouncementSuggestionResponse> suggestion(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(problemAnnouncementService.suggest(year, month));
    }

    @GetMapping
    public ResponseEntity<List<ProblemAnnouncementRecordResponse>> recent() {
        return ResponseEntity.ok(problemAnnouncementService.recent());
    }

    @PostMapping
    public ResponseEntity<Void> announce(@Valid @RequestBody ProblemAnnouncementRequest request) {
        problemAnnouncementService.announce(
                request.content(), request.year(), request.month(), request.weekOfMonth(), request.record());
        return ResponseEntity.noContent().build();
    }

    /** Drops a record sent by mistake, so it stops counting toward the default week. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        problemAnnouncementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
