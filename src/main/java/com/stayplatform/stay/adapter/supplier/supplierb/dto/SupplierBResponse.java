package com.stayplatform.stay.adapter.supplier.supplierb.dto;

public record SupplierBResponse<T>(
        String resultCode,
        String resultMessage,
        T data
) {
    public boolean isSuccess() {
        return "0000".equals(resultCode) && data != null;
    }
}
