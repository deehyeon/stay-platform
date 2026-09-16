package com.stayplatform.global.adapter.mock.handler;

import com.stayplatform.global.adapter.mock.AbstractMockSupplierHandler;
import com.sun.net.httpserver.HttpExchange;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Profile("!test")
public class SupplierAMockHandler extends AbstractMockSupplierHandler {

    @Override
    public String supplierId() {
        return "a";
    }

    @Override
    protected String hotelListPath() {
        return "/a/v1/hotels";
    }

    @Override
    protected String availabilityPath() {
        return "/a/v1/availability";
    }

    @Override
    protected String hotelListResponseBody() {
        return """
                {"items":[{"hotelCode":"A-10023","hotelName":"Mock Hotel A","roomTypes":[{"roomTypeCode":"DLX-TWN","roomTypeName":"Deluxe Twin","maxOccupancy":2}]}]}""";
    }

    @Override
    protected String normalAvailabilityResponseBody() {
        return """
                {"items":[{"hotelCode":"A-10023","roomTypeCode":"DLX-TWN","currency":"KRW","breakfastIncluded":false,"dailyRates":[{"date":"2026-09-01","nightlyRate":100000,"taxAmount":10000,"remainingRooms":5}]}]}""";
    }

    @Override
    protected void handleError(HttpExchange exchange) throws IOException {
        sendText(exchange, 503, "Service Unavailable");
    }
}
