package com.gateway.pubfinder.functions;

import org.apache.logging.log4j.util.Strings;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ResponseBodyRewrite implements RewriteFunction<String, String> {

    Logger log = LoggerFactory.getLogger(ResponseBodyRewrite.class);

    @Override
    public Publisher<String> apply(ServerWebExchange exchange, String responseBody){

        log.info("Outgoing response for url: {}, body: {}", exchange.getRequest().getURI().getPath(), responseBody);

        if(!Strings.isBlank(responseBody) ) {

            return Mono.just(responseBody);
        } else {
            return Mono.just("");
        }

    }
}
