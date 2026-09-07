package com.ipl.order.external;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ClientClient {
    private final WebClient http;
    private final String baseUrl;

    public ClientClient(WebClient http, @Value("${external.client-service}") String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    public ClientDto getClient(Long id) {
        return http.get()
                .uri(baseUrl + "/clients/{id}", id)
                .retrieve()
                // 404 → map to domain-friendly error
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new EntityNotFoundException("CLIENT_NOT_FOUND")))
                // any other 4xx
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> Mono.error(new RuntimeException("CLIENT_BAD_REQUEST")))
                // 5xx
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new RuntimeException("CLIENT_SERVICE_UNAVAILABLE")))
                .bodyToMono(ClientDto.class)
                .block();
    }

    public static class ClientDto {
        public Long id;
        public String name;
    }
}
