package com.stayplatform.stay.exception;

import com.stayplatform.global.exception.common.GlobalErrorType;

public class SupplierUnavailableException extends SupplierException {
    public SupplierUnavailableException() {
        super(GlobalErrorType.SUPPLIER_UNAVAILABLE);
    }
}
