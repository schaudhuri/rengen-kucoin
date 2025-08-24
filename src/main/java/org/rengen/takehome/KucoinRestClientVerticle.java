package org.rengen.takehome;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class KucoinRestClientVerticle extends AbstractVerticle {

    private WebClient client;

    @Override
    public void start() {
        JsonArray symbols = config().getJsonArray("symbols");
        client = WebClient.create(vertx, new WebClientOptions().setSsl(true).setTrustAll(true));

        vertx.eventBus().consumer("orderbook.refresh", message -> {
            JsonArray symbolsToFetch = new JsonArray();
            if (message.body() instanceof String) {
                symbolsToFetch.add((String) message.body());
            } else if (symbols != null) {
                symbolsToFetch = symbols;
            }
            callRestAPIAndSendToEventBus(symbolsToFetch);
        });

        vertx.eventBus().consumer("orderbook.getSnapshot", message -> {
            String symbol = (String) message.body();
            fetchOrderBookSnapshot(symbol, ar -> {
                if (ar.succeeded()) {
                    message.reply(ar.result());
                } else {
                    message.fail(1, ar.cause().getMessage());
                }
            });
        });

        if (symbols != null) {
            callRestAPIAndSendToEventBus(symbols);
        }
    }

    private void callRestAPIAndSendToEventBus(JsonArray symbols) {
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.getString(i);
            fetchOrderBookSnapshot(symbol, ar -> {
                if (ar.succeeded()) {
                    JsonObject payload = new JsonObject()
                            .put("symbol", symbol)
                            .put("snapshot", ar.result());
                    vertx.eventBus().publish("orderbook.snapshot", payload.encode());
                    System.out.println("snapshot sent to event bus for " + symbol + ": " + ar.result());
                } else {
                    System.err.println("Failed to get response for symbol " + symbol + ": " + ar.cause().getMessage());
                }
            });
        }
    }

    private void fetchOrderBookSnapshot(String symbol, io.vertx.core.Handler<io.vertx.core.AsyncResult<String>> handler) {
        client.get(443, "api.kucoin.com", "/api/v1/market/orderbook/level2_20?symbol=" + symbol)
                .send(ar -> {
                    if (ar.succeeded()) {
                        handler.handle(io.vertx.core.Future.succeededFuture(ar.result().bodyAsString()));
                    } else {
                        handler.handle(io.vertx.core.Future.failedFuture(ar.cause()));
                    }
                });
    }
}
