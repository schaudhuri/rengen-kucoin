package org.rengen.takehome;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class Main extends AbstractVerticle {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions()
                        .setType("file")
                        .setConfig(new JsonObject().put("path", "config.json"))));

        retriever.getConfig(ar -> {
            if (ar.succeeded()) {
                JsonObject config = ar.result();
                DeploymentOptions options = new DeploymentOptions().setConfig(config);

                vertx.deployVerticle(new KucoinOrderBookVerticle(), options);
                vertx.deployVerticle(new KucoinRestClientVerticle(), options);
                vertx.deployVerticle(new KucoinWSClientVerticle(), options);
                vertx.deployVerticle(new AdminRestVerticle());
            } else {
                System.out.println("Failed to load config: " + ar.cause());
            }
        });


    }
}
