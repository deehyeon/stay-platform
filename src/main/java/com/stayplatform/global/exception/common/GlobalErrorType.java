package com.stayplatform.global.exception.common;

import com.stayplatform.global.exception.ErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorType implements ErrorType {
    // Common
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 내부 오류입니다."),
    FAILED_REQUEST_VALIDATION(HttpStatus.BAD_REQUEST, "요청 데이터 검증에 실패하였습니다."),
    INVALID_REQUEST_ARGUMENT(HttpStatus.BAD_REQUEST, "잘못된 요청 인자입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패하였습니다."),

    // Search
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "체크아웃은 체크인보다 늦어야 합니다."),
    INVALID_ADULT_COUNT(HttpStatus.BAD_REQUEST, "성인 인원은 1명 이상이어야 합니다."),

    // Supplier
    SUPPLIER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "공급사 연동에 실패하였습니다.");

    private final HttpStatus status;
    private final String message;
}
