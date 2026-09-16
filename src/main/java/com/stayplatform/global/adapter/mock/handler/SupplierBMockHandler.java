package com.stayplatform.global.adapter.mock.handler;

import com.stayplatform.global.adapter.mock.AbstractMockSupplierHandler;
import com.sun.net.httpserver.HttpExchange;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Profile("!test")
public class SupplierBMockHandler extends AbstractMockSupplierHandler {

    @Override
    public String supplierId() {
        return "b";
    }

    @Override
    protected String hotelListPath() {
        return "/b/api/properties";
    }

    @Override
    protected String availabilityPath() {
        return "/b/api/search";
    }

    @Override
    protected String hotelListResponseBody() {
        return """
                {"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[{"propertyId":"B77120","propertyName":"Mock Hotel B","rooms":[{"roomId":"R001","roomName":"Standard Double","maxOccupancy":2}]}]}}""";
    }

    @Override
    protected String normalAvailabilityResponseBody() {
        return """
                {"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[{"propertyId":"B77120","roomId":"R001","totalPrice":250000,"currency":"KRW","breakfastIncluded":true,"inventory":[{"date":"2026-09-01","remainingRooms":3}]}]}}""";
    }

    @Override
    protected void handleError(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, """
                {"resultCode":"E503","resultMessage":"TEMPORARILY_UNAVAILABLE","data":null}""");
    }
}
