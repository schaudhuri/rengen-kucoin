package org.rengen.takehome;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.websocket.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

@ClientEndpoint
public class KucoinWSClientVerticle extends AbstractVerticle {

    private volatile Session userSession = null;
    private EventBus eventBus;

    static final long RECONNECT_DELAY_MS = 1000; // 1 second reconnect delay
    private JsonArray symbols;

    // Flag to control reconnection behavior
    private volatile boolean autoReconnect = true;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        eventBus = vertx.eventBus();
        symbols = config().getJsonArray("symbols");
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("No symbols configured for KucoinWSClientVerticle");
        }

        // Listen on event bus for control commands
        eventBus.consumer("websocket.stop", message -> {
            System.out.println("Received websocket.stop command");
            autoReconnect = false;
            closeSession();
            message.reply("WebSocket stopped");
        });

        eventBus.consumer("websocket.start", message -> {
            System.out.println("Received websocket.start command");
            if (userSession == null || !userSession.isOpen()) {
                autoReconnect = true; // re-enable auto reconnect when start manually
                connectWebSocket();
                message.reply("WebSocket started");
            } else {
                message.reply("WebSocket already connected");
            }
        });

        eventBus.consumer("websocket.restart", message -> {
            System.out.println("Received websocket.restart command");
            autoReconnect = true;
            closeSession();
            // reconnect will be attempted after close event triggers
            message.reply("WebSocket restart requested");
        });

        connectWebSocket();

        startPromise.complete();
    }

    void connectWebSocket() {
        vertx.executeBlocking(promise -> {
            try {
                String wsUrl = getPublicToken();
                System.out.println("Connecting to WebSocket: " + wsUrl);

                URI endpointURI = new URI(wsUrl);
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();

                container.connectToServer(this, endpointURI);
                promise.complete();
            } catch (Exception e) {
                System.err.println("WebSocket connection failed: " + e.getMessage());
                promise.fail(e);
            }
        }, res -> {
            if (res.failed()) {
                System.out.println("Failed to connect WebSocket; retrying in " + RECONNECT_DELAY_MS + "ms");
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (!autoReconnect) {
            System.out.println("Auto reconnect disabled; will not reconnect automatically.");
            return;
        }
        vertx.setTimer(RECONNECT_DELAY_MS, id -> {
            System.out.println("Attempting WebSocket reconnect...");
            connectWebSocket();
        });
    }

    private void closeSession() {
        if (userSession != null && userSession.isOpen()) {
            try {
                userSession.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Manual close"));
            } catch (Exception e) {
                System.err.println("Error closing WebSocket session: " + e.getMessage());
            }
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("WebSocket connected");
        userSession = session;

        StringBuilder topicBuilder = new StringBuilder("/market/level2:");
        for (int i = 0; i < symbols.size(); i++) {
            topicBuilder.append(symbols.getString(i));
            if (i < symbols.size() - 1) {
                topicBuilder.append(",");
            }
        }

        JsonObjectBuilder subscribeMsgBuilder = Json.createObjectBuilder()
                .add("id", 1234)
                .add("type", "subscribe")
                .add("topic", topicBuilder.toString())
                .add("response", true);

        String subscribeMsg = subscribeMsgBuilder.build().toString();

        System.out.println("Sending subscribe message: " + subscribeMsg);
        session.getAsyncRemote().sendText(subscribeMsg);

        // Request fresh snapshots on reconnect for all symbols
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.getString(i);
            vertx.eventBus().send("orderbook.refresh", symbol);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("WebSocket closed: " + reason);
        userSession = null;

        if (autoReconnect) {
            System.out.println("Auto reconnect enabled - scheduling reconnect");
            scheduleReconnect();
        } else {
            System.out.println("Auto reconnect disabled - not reconnecting");
        }
    }

    @OnMessage
    public void onMessage(String message) {
        try {
            JsonObject json = new JsonObject(message);
            String symbol = extractSymbol(json);
            if (symbol == null) {
                System.out.println("No symbol found in message topic, ignoring.");
                return;
            }
            eventBus.publish("orderbook.updates", message);
        } catch (Exception e) {
            System.err.println("Failed to process message: " + e.getMessage());
        }
    }

    @OnError
    public void onError(Throwable t) {
        System.err.println("WebSocket error:");
        t.printStackTrace();
        if (userSession != null) {
            try {
                userSession.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String extractSymbol(JsonObject json) {
        String topic = json.getString("topic", null);
        if (topic == null) {
            System.out.println("Message missing topic field: " + json.encode());
            return null;
        }

        if (topic.contains(":")) {
            String symbolPart = topic.substring(topic.indexOf(":") + 1).toUpperCase();
            if (symbolPart.contains(",")) {
                return symbolPart.split(",")[0];
            } else {
                return symbolPart;
            }
        }

        System.out.println("Topic field malformed (no colon): " + topic);
        return null;
    }

    static String getPublicToken() throws Exception {
        URL url = new URL("https://api.kucoin.com/api/v1/bullet-public");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("Failed to get token: HTTP status " + status);
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        try (javax.json.JsonReader jsonReader = Json.createReader(br)) {
            javax.json.JsonObject jsonResponse = jsonReader.readObject();

            if (!"200000".equals(jsonResponse.getString("code"))) {
                throw new RuntimeException("Error response: " + jsonResponse);
            }

            javax.json.JsonObject data = jsonResponse.getJsonObject("data");
            String token = data.getString("token");
            String endpoint = data.getJsonArray("instanceServers").getJsonObject(0).getString("endpoint");

            if (endpoint.endsWith("/")) {
                endpoint = endpoint.substring(0, endpoint.length() - 1);
            }

            return endpoint + "/endpoint?token=" + token;
        } finally {
            br.close();
        }
    }
}
