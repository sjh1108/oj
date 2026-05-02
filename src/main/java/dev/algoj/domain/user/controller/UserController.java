package dev.algoj.domain.user.controller;

import dev.algoj.domain.submission.service.SubmissionService;
import dev.algoj.domain.user.dto.UserResponse;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import dev.algoj.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final SubmissionService submissionService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/me/solved-problems")
    public ResponseEntity<List<Long>> solvedProblems(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(submissionService.solvedProblemIds(principal.getId()));
    }
}
