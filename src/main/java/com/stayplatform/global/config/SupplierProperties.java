package com.stayplatform.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supplier")
public record SupplierProperties(
        SupplierConfig a,
        SupplierConfig b
) {
    public record SupplierConfig(
            String baseUrl,
            String apiKey,
            TimeoutConfig timeout
    ) {
        public record TimeoutConfig(int connectMs, int responseMs) {
        }
    }
}
