package com.stayplatform.stay.exception;

import com.stayplatform.global.exception.ErrorType;
import com.stayplatform.global.exception.common.GlobalException;

public class SupplierException extends GlobalException {
    public SupplierException(ErrorType errorType) {
        super(errorType);
    }
}
