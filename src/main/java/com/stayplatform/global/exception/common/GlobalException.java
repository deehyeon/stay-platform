package com.stayplatform.global.exception.common;

import com.stayplatform.global.exception.ErrorType;
import lombok.Getter;

@Getter
public class GlobalException extends RuntimeException {
    private final ErrorType errorType;

    public GlobalException(ErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
    }
}
