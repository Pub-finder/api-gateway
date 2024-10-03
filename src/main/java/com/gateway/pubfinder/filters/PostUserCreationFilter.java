package com.gateway.pubfinder.filters;

import com.gateway.pubfinder.dto.AuthenticationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class PostUserCreationFilter extends AbstractGatewayFilterFactory<PostUserCreationFilter.Config> {

    @Value("${auth-uri}")
    private String AUTH_URI;

    final Logger logger = LoggerFactory.getLogger(PostUserCreationFilter.class);

    private final WebClient webClient;

    public PostUserCreationFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClient = webClientBuilder.baseUrl(AUTH_URI).build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpResponse originalResponse = exchange.getResponse();
            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                public Mono<Void> writeWith(Flux<DataBuffer> body) {
                    HttpHeaders headers = getDelegate().getHeaders();
                    String userId = headers.getFirst("X-User-Id");

                    if (userId != null && getDelegate().getStatusCode() != null && getDelegate().getStatusCode().is2xxSuccessful()) {
                        return webClient.get()
                                .uri("/auth/auth-service/generateToken?userId=" + userId)
                                .retrieve()
                                .bodyToMono(AuthenticationResponse.class)
                                .flatMap(authResponse -> {
                                    String responseJson = String.format(
                                            "{\"accessToken\":\"%s\", \"refreshToken\":\"%s\"}",
                                            authResponse.getAccessToken(), authResponse.getRefreshToken());

                                    byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
                                    DataBuffer buffer = originalResponse.bufferFactory().wrap(bytes);

                                    getDelegate().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                                    getDelegate().getHeaders().setContentLength(bytes.length);

                                    return getDelegate().writeWith(Mono.just(buffer));
                                });
                    }

                    return super.writeWith(body);
                }
            };

            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        };
    }

    public static class Config {
    }
}

