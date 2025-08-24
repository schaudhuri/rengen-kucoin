package org.rengen.takehome;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class KucoinOrderBookVerticle extends AbstractVerticle {

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastRefreshTimestamp = new ConcurrentHashMap<>();
    private static final long REFRESH_COOLDOWN_MS = 5000;
    private final ConcurrentMap<String, Boolean> refreshInProgress = new ConcurrentHashMap<>();

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        router.get("/orderbook/:symbol").handler(ctx -> {
            String symbol = ctx.pathParam("symbol").toUpperCase();
            OrderBook book = orderBooks.get(symbol);
            if (book == null) {
                ctx.response().setStatusCode(404).end("Order book not found for symbol: " + symbol);
            } else {
                ctx.response().putHeader("Content-Type", "application/json").end(book.toJson());
            }
        });

        router.get("/orderbook/validate/:symbol").handler(ctx -> {
            String symbol = ctx.pathParam("symbol").toUpperCase();
            OrderBook myBook = orderBooks.get(symbol);
            if (myBook == null) {
                ctx.response().setStatusCode(404).end("Order book not found for symbol: " + symbol);
                return;
            }

            vertx.eventBus().<String>request("orderbook.getSnapshot", symbol, reply -> {
                if (reply.succeeded()) {
                    try {
                        JsonObject officialSnapshotResponse = new JsonObject(reply.result().body());
                        JsonObject officialSnapshot = officialSnapshotResponse.getJsonObject("data");
                        if (officialSnapshot == null) {
                            ctx.response().setStatusCode(500).end("Invalid official snapshot received");
                            return;
                        }
                        JsonObject diff = compareOrderBooks(myBook, officialSnapshot);

                        JsonObject response = new JsonObject();
                        response.put("bids_match_percentage", diff.getDouble("bids_match_percentage"));
                        response.put("asks_match_percentage", diff.getDouble("asks_match_percentage"));
                        response.put("booksMatch", diff.getBoolean("booksMatch"));

                        JsonObject diffs = new JsonObject();
                        diffs.put("bids_diff", diff.getJsonArray("bids_diff"));
                        diffs.put("asks_diff", diff.getJsonArray("asks_diff"));
                        response.put("diffs", diffs);

                        ctx.response()
                                .putHeader("Content-Type", "application/json")
                                .end(response.encodePrettily());
                    } catch (Exception e) {
                        ctx.response().setStatusCode(500).end("Failed to process official snapshot: " + e.getMessage());
                    }
                } else {
                    ctx.response().setStatusCode(500).end("Failed to fetch official snapshot: " + reply.cause().getMessage());
                }
            });
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080)
                .onSuccess(server -> {
                    System.out.println("HTTP server started on port " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);

        vertx.eventBus().consumer("orderbook.snapshot", message -> {
            JsonObject obj = new JsonObject((String) message.body());
            String symbol = obj.getString("symbol");
            String snapshotStr = obj.getString("snapshot");
            if (symbol == null || snapshotStr == null) {
                System.err.println("Invalid snapshot message received");
                return;
            }
            JsonObject snapshotJson = new JsonObject(snapshotStr);
            handleSnapshotMessage(symbol, snapshotJson);
        });

        vertx.eventBus().consumer("orderbook.updates", message -> {
            JsonObject json = new JsonObject((String) message.body());
            handleUpdateMessage(json);
        });
    }

    private void handleSnapshotMessage(String symbol, JsonObject snapshotJson) {
        OrderBook book = orderBooks.computeIfAbsent(symbol, k -> new OrderBook());

        JsonObject data = snapshotJson.getJsonObject("data");
        if (data == null) {
            System.err.println("Snapshot data missing for symbol " + symbol);
            return;
        }
        refreshInProgress.remove(symbol);

        try {
            long sequence;
            Object seqObj = data.getValue("sequence");
            if (seqObj instanceof String) {
                sequence = Long.parseLong((String) seqObj);
            } else if (seqObj instanceof Number) {
                sequence = ((Number) seqObj).longValue();
            } else {
                sequence = -1L;
            }
            if (sequence != -1L) {
                book.setLastSequence(sequence);
            }
        } catch (Exception e) {
            System.err.println("Error parsing sequence in snapshot for " + symbol + ": " + e.getMessage());
        }

        book.applySnapshot(data);
    }

    public void handleUpdateMessage(JsonObject json) {
        String symbol = getSymbol(json);
        if (symbol == null) {
            System.out.println("No symbol found in message");
            return;
        }
        JsonObject data = json.getJsonObject("data");
        if (data == null) {
            return;
        }

        OrderBook book = orderBooks.computeIfAbsent(symbol, k -> new OrderBook());

        handleIncrementalUpdate(symbol, book, data);
    }

    private final Map<String, List<JsonObject>> incrementalUpdateBuffer = new ConcurrentHashMap<>();

    private void handleIncrementalUpdate(String symbol, OrderBook book, JsonObject data) {
        long sequenceStart = data.getLong("sequenceStart", -1L);
        long sequenceEnd = data.getLong("sequenceEnd", -1L);

        if (sequenceEnd == -1) {
            return;
        }

        // Ignore stale messages
        if (sequenceEnd <= book.getLastSequence()) {
            return;
        }

        // Detect gap
        if (book.getLastSequence() != -1 && sequenceStart > book.getLastSequence() + 1) {
            // Buffer this update instead of discarding
            incrementalUpdateBuffer.computeIfAbsent(symbol, k -> new ArrayList<>()).add(data);

            if (!refreshInProgress.containsKey(symbol)) {
                long now = System.currentTimeMillis();
                long lastRefresh = lastRefreshTimestamp.getOrDefault(symbol, 0L);
                if (now - lastRefresh > REFRESH_COOLDOWN_MS) {
                    vertx.eventBus().send("orderbook.refresh", symbol);
                    lastRefreshTimestamp.put(symbol, now);
                    refreshInProgress.put(symbol, true);
//                    System.out.println("refreshing order book for " + symbol);
                }
            }
            else {
                System.out.println("refresh already in progress for " + symbol + ", buffering incremental update");
            }
            return;
        }

        // If any buffered increments exist and sequenceStart is now continuous, apply them first
        List<JsonObject> buffered = incrementalUpdateBuffer.get(symbol);
        if (buffered != null && !buffered.isEmpty()) {
            buffered.sort(Comparator.comparingLong(o -> o.getLong("sequenceEnd", -1L)));
            for (JsonObject bufferedUpdate : buffered) {
                if (bufferedUpdate.getLong("sequenceStart", -1L) == book.getLastSequence() + 1) {
                    book.applyIncremental(bufferedUpdate.getJsonObject("changes"));
                    book.setLastSequence(bufferedUpdate.getLong("sequenceEnd"));
                } else {
                    // If gap still not continuous, break to wait for snapshot refresh
                    break;
                }
            }
            buffered.clear();
        }

        // Apply current incremental update if continuous
        if (sequenceStart == book.getLastSequence() + 1) {
            book.applyIncremental(data.getJsonObject("changes"));
            book.setLastSequence(sequenceEnd);
        } else {
            // If not continuous, buffer current update too
            incrementalUpdateBuffer.computeIfAbsent(symbol, k -> new ArrayList<>()).add(data);
        }
    }



    private String getSymbol(JsonObject json) {
        String topic = json.getString("topic");
        if (topic != null && topic.contains(":")) {
            return topic.substring(topic.indexOf(":") + 1).toUpperCase();
        }
        return null;
    }

    private JsonObject compareOrderBooks(OrderBook myBook, JsonObject officialSnapshot) {
        JsonObject diff = new JsonObject();

        Map<Double, Double> officialBids = toMap(officialSnapshot.getJsonArray("bids"));
        Map<Double, Double> officialAsks = toMap(officialSnapshot.getJsonArray("asks"));

        Map<Double, Double> myBids = myBook.getBids();
        Map<Double, Double> myAsks = myBook.getAsks();

        JsonArray bidsDiff = new JsonArray();
        compareSideDiff(myBids, officialBids, bidsDiff);

        JsonArray asksDiff = new JsonArray();
        compareSideDiff(myAsks, officialAsks, asksDiff);

        diff.put("bids_diff", bidsDiff);
        diff.put("asks_diff", asksDiff);

        double bidsMatchPercent = calculateMatchPercentage(myBids, officialBids);
        double asksMatchPercent = calculateMatchPercentage(myAsks, officialAsks);
        diff.put("bids_match_percentage", bidsMatchPercent);
        diff.put("asks_match_percentage", asksMatchPercent);

        diff.put("booksMatch", bidsDiff.isEmpty() && asksDiff.isEmpty());

        return diff;
    }

    private Map<Double, Double> toMap(JsonArray sideArray) {
        Map<Double, Double> map = new ConcurrentHashMap<>();
        if (sideArray == null) return map;

        for (int i = 0; i < sideArray.size(); i++) {
            JsonArray level = sideArray.getJsonArray(i);
            if (level != null && level.size() >= 2) {
                double price = Double.parseDouble(level.getString(0));
                double size = Double.parseDouble(level.getString(1));
                map.put(price, size);
            }
        }
        return map;
    }

    private void compareSideDiff(Map<Double, Double> mySide, Map<Double, Double> officialSide, JsonArray diffArray) {
        Set<Double> prices = new HashSet<>();
        prices.addAll(mySide.keySet());
        prices.addAll(officialSide.keySet());

        for (Double price : prices) {
            double mySize = mySide.getOrDefault(price, 0.0);
            double officialSize = officialSide.getOrDefault(price, 0.0);
            if (Math.abs(mySize - officialSize) > 1e-6) {
                diffArray.add(new JsonObject()
                        .put("price", price)
                        .put("mySize", mySize)
                        .put("officialSize", officialSize));
            }
        }
    }

    private double calculateMatchPercentage(Map<Double, Double> mySide, Map<Double, Double> officialSide) {
        double matchedVolume = 0.0;
        double totalVolume = 0.0;

        for (double size : officialSide.values()) {
            totalVolume += size;
        }

        if (totalVolume == 0) return 100.0;

        Set<Double> prices = new HashSet<>();
        prices.addAll(mySide.keySet());
        prices.addAll(officialSide.keySet());

        for (Double price : prices) {
            double mySize = mySide.getOrDefault(price, 0.0);
            double officialSize = officialSide.getOrDefault(price, 0.0);
            matchedVolume += Math.min(mySize, officialSize);
        }

        return (matchedVolume / totalVolume) * 100.0;
    }

    public Map<String, OrderBook> getOrderBooks() {
        return orderBooks;
    }
}
