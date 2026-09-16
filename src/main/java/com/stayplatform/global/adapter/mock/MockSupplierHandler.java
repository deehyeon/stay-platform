package com.stayplatform.global.adapter.mock;

import com.sun.net.httpserver.HttpServer;

public interface MockSupplierHandler {

    /**
     * 모드 전환 API에서 공급사를 식별하는 키 ("a", "b", ...)
     */
    String supplierId();

    /**
     * HttpServer에 이 공급사의 엔드포인트를 등록한다.
     */
    void register(HttpServer server);

    /**
     * 현재 응답 모드를 변경한다.
     */
    void setMode(MockSupplierMode mode);
}
