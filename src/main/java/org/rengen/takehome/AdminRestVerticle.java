package org.rengen.takehome;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;

public class AdminRestVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        // GET endpoint to force stop the WebSocket (disable auto reconnect)
        router.get("/admin/websocket/stop").handler(ctx -> {
            vertx.eventBus().request("websocket.stop", "", reply -> {
                if (reply.succeeded()) {
                    ctx.response()
                            .putHeader("content-type", "application/json")
                            .end("{\"status\":\"WebSocket stopped\"}");
                } else {
                    ctx.response().setStatusCode(500).end("Failed to stop WebSocket: " + reply.cause().getMessage());
                }
            });
        });

        // GET endpoint to force start the WebSocket (no auto reconnect)
        router.get("/admin/websocket/start").handler(ctx -> {
            vertx.eventBus().request("websocket.start", "", reply -> {
                if (reply.succeeded()) {
                    ctx.response()
                            .putHeader("content-type", "application/json")
                            .end("{\"status\":\"WebSocket started\"}");
                } else {
                    ctx.response().setStatusCode(500).end("Failed to start WebSocket: " + reply.cause().getMessage());
                }
            });
        });

        // GET endpoint to restart the WebSocket with auto reconnect enabled
        router.get("/admin/websocket/restart").handler(ctx -> {
            vertx.eventBus().request("websocket.restart", "", reply -> {
                if (reply.succeeded()) {
                    ctx.response()
                            .putHeader("content-type", "application/json")
                            .end("{\"status\":\"WebSocket restart requested\"}");
                } else {
                    ctx.response().setStatusCode(500).end("Failed to restart WebSocket: " + reply.cause().getMessage());
                }
            });
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8081)
                .onSuccess(server -> {
                    System.out.println("Admin REST server started on port " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }
}
