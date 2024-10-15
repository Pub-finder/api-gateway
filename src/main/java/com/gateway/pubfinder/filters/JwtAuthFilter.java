package com.gateway.pubfinder.filters;

import com.gateway.pubfinder.dto.TokenValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    @Value("${auth-uri}")
    private String AUTH_URI;

    final Logger logger =
            LoggerFactory.getLogger(JwtAuthFilter.class);

    private final WebClient webClient;

    public JwtAuthFilter() {
        super(Config.class);
        this.webClient = WebClient.create();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("Missing or invalid Authorization header");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String jwt = authHeader.substring(7);

            return this.webClient
                    .get()
                    .uri(AUTH_URI+"/auth/validateToken/{token}", jwt)
                    .retrieve()
                    .bodyToMono(TokenValidationResponse.class)
                    .flatMap(response -> {
                        if (TokenValidationResponse.VALID.equals(response)) {
                            return chain.filter(exchange);
                        } else if (TokenValidationResponse.EXPIRED.equals(response)) {
                            logger.warn("Expired Token");
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        } else {
                            logger.warn("Invalid Token");
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                    })
                    .onErrorResume(e -> {
                        logger.error("Error while validating token", e);
                        if (e instanceof java.net.ConnectException) {
                            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                        } else {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        }
                        return exchange.getResponse().setComplete();
                    });
        };
    }

    public static class Config {
    }
}
