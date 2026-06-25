package dev.algoj.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),

    // Auth / User
    USERNAME_DUPLICATED(HttpStatus.CONFLICT, "U001", "이미 사용 중인 아이디입니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U003", "존재하지 않는 사용자입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A001", "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "A004", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A005", "권한이 없습니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "A006", "현재 비밀번호가 일치하지 않습니다."),

    // Problem / TestCase
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "존재하지 않는 문제입니다."),
    PROBLEM_NOT_ACCESSIBLE(HttpStatus.FORBIDDEN, "P002", "비공개 문제는 관리자만 조회할 수 있습니다."),
    TEST_CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "존재하지 않는 테스트케이스입니다."),
    TEST_CASE_NOT_BELONG_TO_PROBLEM(HttpStatus.BAD_REQUEST, "T002", "해당 문제의 테스트케이스가 아닙니다."),
    NO_TEST_CASES(HttpStatus.BAD_REQUEST, "T003", "채점할 테스트케이스가 없습니다."),

    // Submission / Judge0
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "존재하지 않는 제출입니다."),
    JUDGE0_ERROR(HttpStatus.BAD_GATEWAY, "S002", "채점 서버 호출에 실패했습니다."),
    SOLUTION_LOCKED(HttpStatus.FORBIDDEN, "S003", "해당 문제를 정답 처리한 후에 다른 사람의 풀이를 볼 수 있습니다."),

    // Test case generation
    GENERATOR_FAILED(HttpStatus.BAD_REQUEST, "G001", "테스트케이스 생성기 실행에 실패했습니다."),
    SOLUTION_FAILED(HttpStatus.BAD_REQUEST, "G002", "모범답안 실행에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
