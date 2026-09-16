package com.stayplatform.global.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(SupplierProperties.class)
public class WebClientConfig {

    @Bean("supplierAWebClient")
    public WebClient supplierAWebClient(SupplierProperties properties) {
        return buildWebClient(properties.a());
    }

    @Bean("supplierBWebClient")
    public WebClient supplierBWebClient(SupplierProperties properties) {
        return buildWebClient(properties.b());
    }

    private WebClient buildWebClient(SupplierProperties.SupplierConfig config) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.timeout().connectMs())
                .responseTimeout(Duration.ofMillis(config.timeout().responseMs()));

        return WebClient.builder()
                .baseUrl(config.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("X-Api-Key", config.apiKey())
                .build();
    }
}
