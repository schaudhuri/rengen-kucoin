package org.rengen.takehome;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public class OrderBook {
    private static final double SIZE_THRESHOLD = 1e-4;
    private static final int MAX_DEPTH = 100;

    private long lastSequence = -1L;
    private final Map<Double, Double> bids = new TreeMap<>(Comparator.reverseOrder());
    private final Map<Double, Double> asks = new TreeMap<>();

    public void applySnapshot(JsonObject snapshot) {
        bids.clear();
        asks.clear();
        lastSequence = -1L;

        JsonArray bidsArr = snapshot.getJsonArray("bids");
        if (bidsArr != null) {
            for (int i = 0; i < bidsArr.size(); i++) {
                JsonArray bid = bidsArr.getJsonArray(i);
                if (bid != null && bid.size() == 2) {
                    double price = Double.parseDouble(bid.getString(0));
                    double size = Double.parseDouble(bid.getString(1));
                    if (size >= SIZE_THRESHOLD) bids.put(price, size);
                }
            }
            pruneDepth(bids);
        }

        JsonArray asksArr = snapshot.getJsonArray("asks");
        if (asksArr != null) {
            for (int i = 0; i < asksArr.size(); i++) {
                JsonArray ask = asksArr.getJsonArray(i);
                if (ask != null && ask.size() == 2) {
                    double price = Double.parseDouble(ask.getString(0));
                    double size = Double.parseDouble(ask.getString(1));
                    if (size >= SIZE_THRESHOLD) asks.put(price, size);
                }
            }
            pruneDepth(asks);
        }

        String seqStr = snapshot.getString("sequence");
        if (seqStr != null) {
            try {
                lastSequence = Long.parseLong(seqStr);
            } catch (NumberFormatException ignored) {}
        }
    }

    public void applyIncremental(JsonObject changes) {
        if (changes == null) return;

        JsonArray bidsArr = changes.getJsonArray("bids");
        if (bidsArr != null) {
            for (int i = 0; i < bidsArr.size(); i++) {
                JsonArray bid = bidsArr.getJsonArray(i);
                if (bid != null && bid.size() == 3) {
                    double price = Double.parseDouble(bid.getString(0));
                    double size = Double.parseDouble(bid.getString(1));
                    long seq = Long.parseLong(bid.getString(2));
                    if (seq <= lastSequence) continue;
                    if (size < SIZE_THRESHOLD) bids.remove(price);
                    else bids.put(price, size);
                }
            }
            pruneDepth(bids);
        }

        JsonArray asksArr = changes.getJsonArray("asks");
        if (asksArr != null) {
            for (int i = 0; i < asksArr.size(); i++) {
                JsonArray ask = asksArr.getJsonArray(i);
                if (ask != null && ask.size() == 3) {
                    double price = Double.parseDouble(ask.getString(0));
                    double size = Double.parseDouble(ask.getString(1));
                    long seq = Long.parseLong(ask.getString(2));
                    if (seq <= lastSequence) continue;
                    if (size < SIZE_THRESHOLD) asks.remove(price);
                    else asks.put(price, size);
                }
            }
            pruneDepth(asks);
        }
    }

    // Prune the map to a maximum depth by removing lowest priority entries
    private void pruneDepth(Map<Double, Double> map) {
        while (map.size() > MAX_DEPTH) {
            Double lastKey = ((TreeMap<Double, Double>) map).lastKey();
            if (lastKey != null) {
                map.remove(lastKey);
            } else {
                break;
            }
        }
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(long sequence) {
        this.lastSequence = sequence;
    }

    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.put("sequence", lastSequence);

        JsonArray bidsArr = new JsonArray();
        bids.forEach((price, size) -> bidsArr.add(new JsonArray().add(price).add(size)));
        obj.put("bids", bidsArr);

        JsonArray asksArr = new JsonArray();
        asks.forEach((price, size) -> asksArr.add(new JsonArray().add(price).add(size)));
        obj.put("asks", asksArr);

        return obj.encodePrettily();
    }

    public Map<Double, Double> getBids() {
        return bids;
    }

    public Map<Double, Double> getAsks() {
        return asks;
    }

}