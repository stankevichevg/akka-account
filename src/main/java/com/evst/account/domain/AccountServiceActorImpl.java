package com.evst.account.domain;

import akka.actor.ActorRef;
import com.evst.account.domain.Message.AccountSnapshotResponse;
import com.evst.account.domain.Message.TransferSnapshotResponse;
import com.typesafe.config.Config;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;

import static akka.pattern.Patterns.ask;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class AccountServiceActorImpl implements AccountService {

    private static final String CREATE_ACCOUNT_TIMEOUT = "timeout.account.create";
    private static final String RETRIEVE_ACCOUNT_TIMEOUT = "timeout.account.retrieve";
    private static final String RETRIEVE_TRANSFER_TIMEOUT = "timeout.transfer.retrieve";
    private static final String MAKE_TRANSFER_TIMEOUT = "timeout.transfer.make";
    private static final String DEPOSIT_TIMEOUT = "timeout.account.deposit";

    private final ActorRef accountManager;
    private final Config config;

    @Inject
    public AccountServiceActorImpl(@Named("accounts") ActorRef accountManager, Config config) {
        this.accountManager = accountManager;
        this.config = config;
    }

    @Override
    public CompletionStage<UUID> createAccount(UUID id, String name) throws AccountAlreadyExistsException {
        checkNotNull(id, "Request id can not be null");
        checkNotNull(name, "Name can not be null");
        return ask(
            accountManager,
            new Message.CreateAccountCommand(id, name),
            config.getDuration(CREATE_ACCOUNT_TIMEOUT)
        ).thenApply(message -> {
            if (message instanceof Message.AccountCreatedResponse) {
                return ((Message.AccountCreatedResponse) message).getId();
            } else if (message instanceof Message.AccountAlreadyExistsResponse) {
                final UUID existAccountId = ((Message.AccountAlreadyExistsResponse) message).getId();
                throw new AccountAlreadyExistsException(
                    existAccountId, format("Account %s already exists", existAccountId)
                );
            } else {
                throw unsupportedAccountResponse(message);
            }
        });
    }

    @Override
    public CompletionStage<AccountState> retrieveAccount(UUID id) {
        checkNotNull(id, "Account id can not be null");
        return ask(
            accountManager,
            new Message.RetrieveAccountCommand(id),
            config.getDuration(RETRIEVE_ACCOUNT_TIMEOUT)
        ).thenApply(message -> {
            if (message instanceof AccountSnapshotResponse) {
                return ((AccountSnapshotResponse) message).getAccount();
            } else if (message instanceof Message.AccountNotFoundResponse) {
                final UUID notFoundId = ((Message.AccountNotFoundResponse) message).getId();
                throw new AccountNotFoundException(
                    notFoundId, format("Account %s not found", notFoundId)
                );
            } else {
                throw unsupportedAccountResponse(message);
            }
        });
    }

    @Override
    public CompletionStage<TransferState> makeTransfer(UUID uniqueId, UUID source, UUID target, BigDecimal amount)
        throws AccountNotFoundException, TransferAlreadyExistsException {
        checkNotNull(uniqueId, "Unique id can not be null");
        checkNotNull(source, "Source account id can not be null");
        checkNotNull(target, "Target account id can not be null");
        checkNotNull(amount, "Amount can not be null");
        return ask(
            accountManager,
            new Message.MakeTransferCommand(uniqueId, source, target, amount),
            config.getDuration(MAKE_TRANSFER_TIMEOUT)
        ).thenApply(message -> {
            if (message instanceof Message.TransferResponse) {
                return ((Message.TransferResponse) message).getTransferState();
            } else if (message instanceof Message.TransferAlreadyExistsResponse) {
                final UUID transferId = ((Message.TransferAlreadyExistsResponse) message).getId();
                throw new TransferAlreadyExistsException(
                    transferId, format("Transfer with id %s has been already created", transferId)
                );
            } else if (message instanceof Message.AccountNotFoundResponse) {
                final UUID notFoundId = ((Message.AccountNotFoundResponse) message).getId();
                throw new AccountNotFoundException(
                    notFoundId, format("Account %s not found", notFoundId)
                );
            } else if (message instanceof Message.TransferRequestIsBeingCreated) {
                final UUID id = ((Message.TransferRequestIsBeingCreated) message).getId();
                throw new TransferIsBeingCreatedException(
                    id, format("Transfer %s is being created", id)
                );
            } else {
                throw unsupportedAccountResponse(message);
            }
        });
    }

    @Override
    public CompletionStage<TransferState> retrieveTransfer(UUID id) throws TransferNotFoundException {
        checkNotNull(id, "Transfer id can not be null");
        return ask(
            accountManager,
            new Message.RetrieveTransferCommand(id),
            config.getDuration(RETRIEVE_TRANSFER_TIMEOUT)
        ).thenApply(message -> {
            if (message instanceof TransferSnapshotResponse) {
                return  ((TransferSnapshotResponse) message).getTransferState();
            } else if (message instanceof Message.TransferNotFoundResponse) {
                final UUID notFoundId = ((Message.TransferNotFoundResponse) message).getId();
                throw new TransferNotFoundException(
                    notFoundId, format("Transfer %s not found", notFoundId)
                );
            } else {
                throw unsupportedAccountResponse(message);
            }
        });
    }

    @Override
    public CompletionStage<TransferState> depositMoney(UUID uniqueId, UUID target, BigDecimal amount) {
        checkNotNull(uniqueId, "Unique id can not be null");
        checkNotNull(target, "Target account id can not be null");
        checkNotNull(amount, "Amount can not be null");
        checkArgument(amount.compareTo(BigDecimal.ZERO) > 0, "Amount has to be positive number");
        return ask(
            accountManager,
            new Message.DepositMoneyCommand(uniqueId, target, amount),
            config.getDuration(DEPOSIT_TIMEOUT)
        ).thenApply(message -> {
            if (message instanceof Message.TransferResponse) {
                return ((Message.TransferResponse) message).getTransferState();
            } else if (message instanceof Message.TransferAlreadyExistsResponse) {
                final UUID transferId = ((Message.TransferAlreadyExistsResponse) message).getId();
                throw new TransferAlreadyExistsException(
                    transferId, format("Transfer with id %s has been already created", transferId)
                );
            } else if (message instanceof Message.AccountNotFoundResponse) {
                final UUID notFoundId = ((Message.AccountNotFoundResponse) message).getId();
                throw new AccountNotFoundException(
                    notFoundId, format("Account %s not found", notFoundId)
                );
            } else if (message instanceof Message.TransferRequestIsBeingCreated) {
                final UUID id = ((Message.TransferRequestIsBeingCreated) message).getId();
                throw new TransferIsBeingCreatedException(
                    id, format("Transfer %s is being created", id)
                );
            } else {
                throw unsupportedAccountResponse(message);
            }
        });
    }

    private RuntimeException unsupportedAccountResponse(Object message) {
        throw new RuntimeException("Unsupported response from the account manager actor");
    }
}
