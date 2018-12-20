package com.seedollar.sandbox.akka;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

public class HttpServerMinimalExampleTest extends AllDirectives {

    public static void main(String[] args) throws IOException {
        ActorSystem actorSystem = ActorSystem.create("routes");

        final Http http = Http.get(actorSystem);
        final ActorMaterializer materializer = ActorMaterializer.create(actorSystem);

        HttpServerMinimalExampleTest app = new HttpServerMinimalExampleTest();

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.createRoute().flow(actorSystem, materializer);

        final CompletionStage<ServerBinding> binding =
                http.bindAndHandle(routeFlow, ConnectHttp.toHost("localhost", 8080), materializer);

        System.out.println("server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read();

        binding.thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> actorSystem.terminate());
    }

    private Route createRoute() {
        return route(
                path("hello",
                        () -> get(() ->
                                complete("<h1>Say hello Akka Http!</h1>"))));
    }
}
