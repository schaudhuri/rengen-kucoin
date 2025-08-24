package org.rengen.takehome;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
    }

    @Test
    void testApplySnapshot() {
        JsonObject snapshot = new JsonObject()
                .put("sequence", "100")
                .put("bids", new JsonArray().add(new JsonArray().add("100.0").add("1.5")))
                .put("asks", new JsonArray().add(new JsonArray().add("101.0").add("2.0")));

        orderBook.applySnapshot(snapshot);

        assertEquals(100L, orderBook.getLastSequence());

        JsonObject jsonOutput = new JsonObject(orderBook.toJson());
        JsonArray bidsArr = jsonOutput.getJsonArray("bids");
        boolean found = false;
        for (int i = 0; i < bidsArr.size(); i++) {
            JsonArray bid = bidsArr.getJsonArray(i);
            if (bid != null && bid.getDouble(0) == 100.0) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected bid price 100.0 in output");
    }


    @Test
    void testApplyIncrementalAddAndRemove() {
        orderBook.setLastSequence(100);

        JsonObject changes = new JsonObject()
                .put("bids",
                        new JsonArray().add(new JsonArray().add("99.5").add("2.0").add("101")))
                .put("asks",
                        new JsonArray().add(new JsonArray().add("101.5").add("0.0").add("101")));

        orderBook.applyIncremental(changes);

        String json = orderBook.toJson();
        assertTrue(json.contains("99.5"));
        assertFalse(json.contains("101.5")); // removed because size < threshold
    }
}
