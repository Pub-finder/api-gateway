package com.gateway.pubfinder.filters;

import com.gateway.pubfinder.dto.AuthenticationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class PostUserCreationFilter extends AbstractGatewayFilterFactory<PostUserCreationFilter.Config> {

    @Value("${auth-uri}")
    private String AUTH_URI;

    final Logger logger = LoggerFactory.getLogger(PostUserCreationFilter.class);

    private final WebClient webClient;

    public PostUserCreationFilter() {
        super(Config.class);
        this.webClient = WebClient.create();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Proceed with the filter chain and handle the response
            return chain.filter(exchange).then(Mono.defer(() -> {
                logger.info("intercepted response");

                var response = exchange.getResponse();
                var headers = response.getHeaders();
                String userId = headers.getFirst("X-User-Id");

                if (userId != null && response.getStatusCode() != null && response.getStatusCode().is2xxSuccessful()) {
                    return webClient
                            .get()
                            .uri(AUTH_URI + "/auth/generateToken/{userId}", userId)
                            .retrieve()
                            .bodyToMono(AuthenticationResponse.class)
                            .flatMap(authResponse -> {
                                response.getHeaders().set("X-ACCESS-TOKEN", authResponse.getAccessToken());
                                response.getHeaders().set("X-REFRESH-TOKEN", authResponse.getRefreshToken());
                                return chain.filter(exchange);
                            })
                            .onErrorResume(e -> {
                                logger.error("Error while creating token", e);
                                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                                return response.setComplete();
                            });
                }

                return chain.filter(exchange);
            }));
        };
    }

    public static class Config {
    }
}

