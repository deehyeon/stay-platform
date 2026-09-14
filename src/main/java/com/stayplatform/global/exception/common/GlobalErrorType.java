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

    // Supplier
    SUPPLIER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "공급사 연동에 실패하였습니다."),
    SUPPLIER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "공급사 응답 시간이 초과되었습니다."),
    SUPPLIER_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "공급사로부터 유효하지 않은 응답을 받았습니다.");

    private final HttpStatus status;
    private final String message;
}
