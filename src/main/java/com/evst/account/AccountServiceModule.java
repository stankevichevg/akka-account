package com.evst.account;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import com.evst.account.domain.AccountManager;
import com.evst.account.domain.AccountService;
import com.evst.account.domain.AccountServiceActorImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class AccountServiceModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        final Config config = ConfigFactory.load("application");
        final ActorSystem actorSystem = ActorSystem.create("account-service", config);
        final ActorRef accountManager = actorSystem.actorOf(Props.create(AccountManager.class), "accounts");

        bind(Config.class).toInstance(config);
        bind(ActorSystem.class).toInstance(actorSystem);
        bind(ActorMaterializer.class).toInstance(ActorMaterializer.create(actorSystem));
        bind(Route.class).toProvider(AccountServiceRouteProvider.class);
        bind(ActorRef.class).annotatedWith(Names.named("accounts")).toInstance(accountManager);
        bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class).asEagerSingleton();
        bind(AccountService.class).to(AccountServiceActorImpl.class).asEagerSingleton();

        bind(HttpServer.class);
    }
}
