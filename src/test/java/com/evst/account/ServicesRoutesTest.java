package com.evst.account;


import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import akka.stream.ActorMaterializer;
import com.evst.account.api.AccountReadData;
import com.evst.account.api.DepositReadData;
import com.evst.account.api.TransferReadData;
import com.evst.account.domain.AbstractPersistedActorTest;
import com.evst.account.domain.AccountManager;
import com.evst.account.domain.AccountService;
import com.evst.account.domain.AccountServiceActorImpl;
import com.evst.account.domain.TransferState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static akka.http.javadsl.model.ContentTypes.APPLICATION_JSON;
import static akka.http.javadsl.model.StatusCodes.BAD_REQUEST;
import static akka.http.javadsl.model.StatusCodes.CONFLICT;
import static akka.http.javadsl.model.StatusCodes.NOT_FOUND;
import static akka.http.javadsl.model.StatusCodes.OK;
import static com.evst.account.Helpers.resourceAsString;
import static com.evst.account.domain.TransferState.Status.COMPLETED;
import static com.evst.account.domain.TransferState.Status.LOW_BALANCE;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ServicesRoutesTest extends JUnitRouteTest {

    private TestRoute route;
    private ObjectMapper om;
    private SystemDelegate systemDelegate;

    @Before
    public void setup() throws IOException {
        systemDelegate = new SystemDelegate();
        systemDelegate.setup();
        final Injector injector = Guice.createInjector(new AccountServiceTestModule());
        route = testRoute(injector.getInstance(Route.class));
        om = injector.getInstance(ObjectMapper.class);
    }

    @After
    public void teardown() {
        systemDelegate.teardown();
        route = null;
    }

    @Test
    public void whenAccountCreatedThenItCanBeRetrieved() {

        final UUID id = UUID.randomUUID();
        final String name = "test";
        route.run(createAccountRequest(id, name))
            .assertStatusCode(OK)
            .assertContentType(APPLICATION_JSON)
            .assertEntityAs(
                Jackson.unmarshaller(om, String.class),
                id.toString()
            );

        route.run(retrieveAccountRequest(id))
            .assertStatusCode(OK)
            .assertContentType(APPLICATION_JSON)
            .assertEntityAs(
                Jackson.unmarshaller(om, AccountReadData.class),
                new AccountReadData(id, name, new BigDecimal(0.0))
            );
    }

    @Test
    public void whenAccountNotCreatedThenItCanNotBeRetrieved() {

        final UUID id = UUID.randomUUID();
        route.run(retrieveAccountRequest(id))
            .assertStatusCode(NOT_FOUND)
            .assertContentType(ContentTypes.TEXT_PLAIN_UTF8);
    }

    @Test
    public void whenAccountCreatedThenItIsAllowedToDeposit() {

        final UUID transferId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(100.0);
        final UUID id = UUID.randomUUID();
        final String name = "test";

        route.run(createAccountRequest(id, name)).assertStatusCode(OK);

        route.run(createDepositRequest(transferId, id, amount))
            .assertStatusCode(OK)
            .assertEntityAs(
                Jackson.unmarshaller(om, DepositReadData.class),
                new DepositReadData(transferId, id, amount, COMPLETED.getCode())
            );

        route.run(createDepositRequest(transferId, id, amount)).assertStatusCode(CONFLICT);
    }

    @Test
    public void whenAccountNotCreatedThenItIsNotAllowedToDeposit() {

        final UUID transferId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(100.0);
        final UUID id = UUID.randomUUID();

        route.run(createDepositRequest(transferId, id, amount))
            .assertStatusCode(NOT_FOUND)
            .assertContentType(ContentTypes.TEXT_PLAIN_UTF8);
    }

    @Test
    public void whenTransferNotCreatedThenItIsNotAllowedToRetrieve() {

        final UUID transferId = UUID.randomUUID();

        route.run(retrieveTransferRequest(transferId))
            .assertStatusCode(NOT_FOUND)
            .assertContentType(ContentTypes.TEXT_PLAIN_UTF8);
    }

    @Test
    public void testSuccessTransferPath() {

        final UUID transferId = UUID.randomUUID();
        final UUID depositTransferId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(100.0);
        final UUID sourceId = UUID.randomUUID();
        final BigDecimal initialSourceBalance = new BigDecimal(100.0);
        final UUID targetId = UUID.randomUUID();

        route.run(createAccountRequest(sourceId, "source")).assertStatusCode(OK);
        route.run(createDepositRequest(depositTransferId, sourceId, initialSourceBalance)).assertStatusCode(OK);
        route.run(createAccountRequest(targetId, "target")).assertStatusCode(OK);

        route.run(createTransferRequest(transferId, sourceId, targetId, amount))
            .assertStatusCode(OK)
            .assertContentType(APPLICATION_JSON)
            .assertEntityAs(
                Jackson.unmarshaller(om, TransferReadData.class),
                new TransferReadData(transferId, sourceId, targetId, amount, COMPLETED.getCode())
            );

        route.run(createTransferRequest(transferId, sourceId, targetId, amount))
            .assertStatusCode(CONFLICT);

        route.run(retrieveTransferRequest(transferId))
            .assertEntityAs(
                Jackson.unmarshaller(om, TransferReadData.class),
                new TransferReadData(transferId, sourceId, targetId, amount, COMPLETED.getCode())
            );

        route.run(retrieveAccountRequest(sourceId))
            .assertEntityAs(
                Jackson.unmarshaller(om, AccountReadData.class),
                new AccountReadData(sourceId, "source", initialSourceBalance.subtract(amount))
            );

        route.run(retrieveAccountRequest(targetId))
            .assertEntityAs(
                Jackson.unmarshaller(om, AccountReadData.class),
                new AccountReadData(targetId, "target", amount)
            );
    }

    @Test
    public void whenInsufficientSourceBalanceThenTransferFail() {
        final UUID transferId = UUID.randomUUID();
        final UUID depositTransferId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(100.0);
        final UUID sourceId = UUID.randomUUID();
        final BigDecimal initialSourceBalance = new BigDecimal(50.0);
        final UUID targetId = UUID.randomUUID();

        route.run(createAccountRequest(sourceId, "source")).assertStatusCode(OK);
        route.run(createAccountRequest(targetId, "target")).assertStatusCode(OK);
        route.run(createDepositRequest(depositTransferId, sourceId, initialSourceBalance)).assertStatusCode(OK);

        route.run(createTransferRequest(transferId, sourceId, targetId, amount))
            .assertStatusCode(OK)
            .assertContentType(APPLICATION_JSON)
            .assertEntityAs(
                Jackson.unmarshaller(om, TransferReadData.class),
                new TransferReadData(transferId, sourceId, targetId, amount, LOW_BALANCE.getCode())
            );
    }

    @Test
    public void whenIncorrectRequestSentThenBadRequestResponse() {

        final UUID transferId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(100.0);
        final UUID id = UUID.randomUUID();
        final String name = "test";

        route.run(
            HttpRequest.POST("/accounts")
                .withEntity(
                    APPLICATION_JSON,
                    String.format(
                        resourceAsString("com/evst/account/api/account_write_data.template"),
                        id.toString() + "addition_to_break",
                        name
                    )
                )
        )
            .assertStatusCode(BAD_REQUEST)
            .assertContentType(ContentTypes.TEXT_PLAIN_UTF8);
    }

    private HttpRequest createAccountRequest(UUID id, String name) {
        return HttpRequest.POST("/accounts")
            .withEntity(
                APPLICATION_JSON,
                String.format(
                    resourceAsString("com/evst/account/api/account_write_data.template"),
                    id,
                    name
                )
            );
    }

    private HttpRequest createDepositRequest(UUID transferId, UUID id, BigDecimal amount) {
        return HttpRequest.POST(String.format("/accounts/%s/deposit", id.toString()))
            .withEntity(
                APPLICATION_JSON,
                String.format(
                    resourceAsString("com/evst/account/api/deposit_write_data.template"),
                    transferId,
                    amount
                )
            );
    }

    private HttpRequest createTransferRequest(UUID transferId, UUID sourceId, UUID targetId, BigDecimal amount) {
        return HttpRequest.POST("/transfers")
            .withEntity(
                APPLICATION_JSON,
                String.format(
                    resourceAsString("com/evst/account/api/transfer_write_data.template"),
                    transferId,
                    sourceId,
                    targetId,
                    amount
                )
            );
    }

    private HttpRequest retrieveAccountRequest(UUID id) {
        return HttpRequest.GET(String.format("/accounts/%s", id.toString()));
    }

    private HttpRequest retrieveTransferRequest(UUID id) {
        return HttpRequest.GET(String.format("/transfers/%s", id.toString()));
    }

    private static class SystemDelegate extends AbstractPersistedActorTest {
    }

    private class AccountServiceTestModule extends AbstractModule {

        @Override
        protected void configure() {
            super.configure();

            bind(Config.class).toInstance(systemDelegate.getConfig());
            bind(ActorSystem.class).toInstance(systemDelegate.getSystem());
            bind(ActorMaterializer.class).toInstance(ActorMaterializer.create(systemDelegate.getSystem()));
            bind(ActorRef.class).annotatedWith(Names.named("accounts")).toInstance(
                systemDelegate.getSystem().actorOf(Props.create(AccountManager.class), "accounts")
            );
            bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class).asEagerSingleton();
            bind(AccountService.class).to(AccountServiceActorImpl.class).asEagerSingleton();
            bind(Route.class).toProvider(AccountServiceRouteProvider.class);
        }
    }

}
