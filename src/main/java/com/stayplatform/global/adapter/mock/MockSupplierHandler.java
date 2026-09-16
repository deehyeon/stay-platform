package com.stayplatform.global.adapter.mock;

import com.sun.net.httpserver.HttpServer;

public interface MockSupplierHandler {

    String supplierId();

    void register(HttpServer server);

    void setMode(MockSupplierMode mode);
}
