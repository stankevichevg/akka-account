package com.evst.account;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.google.inject.Singleton;
import com.typesafe.config.Config;

import java.io.IOException;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;

import static akka.http.javadsl.ConnectHttp.toHost;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
@Singleton
public class HttpServer {

    @Inject Config config;
    @Inject private ActorSystem actorSystem;
    @Inject private ActorMaterializer actorMaterializer;

    @Inject private Route route;

    /**
     * Starts HTTP server. It assumes that Guice module is configured properly to provide configuration,
     * current {@link ActorSystem} and {@link ActorMaterializer} as well as a set up routes object.
     *
     * Routes provided as a separate component of the server to make them independent from each other and
     * to be able to reuse {@link HttpServer}.
     */
    public void start() {
        final Http http = Http.get(this.actorSystem);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = route.flow(actorSystem, actorMaterializer);
        final String host = config.getString("server.host");
        final Integer port = config.getInt("server.port");
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
            routeFlow, toHost(host, port), actorMaterializer
        );

        System.out.format("Server listening at http://%s:%d/\nPress ENTER to stop...", host, port);
        try {
            System.in.read();
        } catch (IOException e) {
            System.out.println(e);
            // ignore
        } finally {
            binding.thenCompose(ServerBinding::unbind).thenAccept(unbound -> actorSystem.terminate());
        }
    }

}
