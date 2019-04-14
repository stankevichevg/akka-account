package com.evst.account;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;
import akka.http.scaladsl.server.ValidationRejection;
import com.evst.account.api.AccountReadData;
import com.evst.account.api.AccountWriteData;
import com.evst.account.api.DepositReadData;
import com.evst.account.api.DepositWriteData;
import com.evst.account.api.Mapper;
import com.evst.account.api.TransferReadData;
import com.evst.account.api.TransferWriteData;
import com.evst.account.domain.AccountService;
import com.evst.account.domain.AccountService.AccountAlreadyExistsException;
import com.evst.account.domain.AccountService.AccountNotFoundException;
import com.evst.account.domain.AccountService.TransferAlreadyExistsException;
import com.evst.account.domain.AccountService.TransferNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;

import java.util.UUID;
import javax.inject.Inject;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.completeOKWithFuture;
import static akka.http.javadsl.server.Directives.concat;
import static akka.http.javadsl.server.Directives.entity;
import static akka.http.javadsl.server.Directives.handleExceptions;
import static akka.http.javadsl.server.Directives.handleRejections;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.PathMatchers.segment;
import static akka.http.javadsl.server.PathMatchers.uuidSegment;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class AccountServiceRouteProvider implements Provider<Route> {

    private final AccountService accountService;
    private final ObjectMapper om;

    @Inject
    public AccountServiceRouteProvider(AccountService accountService, ObjectMapper om) {
        this.accountService = accountService;
        this.om = om;
    }

    @Override
    public Route get() {
        return handleRejections(
            createRejectionsHandler(),
            () -> handleExceptions(
                handleAccountExceptions(),
                () -> concat(
                    Directives.post(() -> path("accounts", () ->
                        entity(Jackson.unmarshaller(om, AccountWriteData.class), entity ->
                            completeOKWithFuture(
                                accountService.createAccount(entity.getId(), entity.getName()),
                                Jackson.<UUID>marshaller(om)
                            )
                        )
                    )),
                    Directives.get(() -> path(segment("accounts").slash(uuidSegment()), id ->
                        completeOKWithFuture(
                            accountService.retrieveAccount(id).thenApply(Mapper::accountData),
                            Jackson.<AccountReadData>marshaller(om)
                        )
                    )),
                    Directives.post(() -> path(segment("accounts").slash(uuidSegment()).slash(segment("deposit")), id ->
                        entity(Jackson.unmarshaller(om, DepositWriteData.class), entity ->
                            completeOKWithFuture(
                                accountService.depositMoney(
                                    entity.getTransferId(), id, entity.getAmount()
                                ).thenApply(Mapper::depositData),
                                Jackson.<DepositReadData>marshaller(om)
                            )
                        )
                    )),
                    Directives.post(() -> path("transfers", () ->
                        entity(Jackson.unmarshaller(om, TransferWriteData.class), entity ->
                            completeOKWithFuture(
                                accountService.makeTransfer(
                                    entity.getTransferId(), entity.getSourceAccountId(),
                                    entity.getTargetAccountId(), entity.getAmount()
                                ).thenApply(Mapper::transferData),
                                Jackson.<TransferReadData>marshaller(om)
                            )
                        )
                    )),
                    Directives.get(() -> path(segment("transfers").slash(PathMatchers.uuidSegment()), id ->
                        completeOKWithFuture(
                            accountService.retrieveTransfer(id).thenApply(Mapper::transferData),
                            Jackson.<TransferReadData>marshaller(om)
                        )
                    ))
                )
            )
        );
    }

    private ExceptionHandler handleAccountExceptions() {
        return ExceptionHandler.newBuilder()
            .match(AccountAlreadyExistsException.class, e -> complete(StatusCodes.CONFLICT, e.getMessage()))
            .match(TransferAlreadyExistsException.class, e -> complete(StatusCodes.CONFLICT, e.getMessage()))
            .match(AccountNotFoundException.class, e -> complete(StatusCodes.NOT_FOUND, e.getMessage()))
            .match(TransferNotFoundException.class, e -> complete(StatusCodes.NOT_FOUND, e.getMessage()))
            .build();
    }

    private RejectionHandler createRejectionsHandler() {
        return RejectionHandler.newBuilder()
            .handle(ValidationRejection.class, rej -> {
                return complete(StatusCodes.BAD_REQUEST, "Bad request. Check API spec before calling.");
            })
            .build();
    }
}
