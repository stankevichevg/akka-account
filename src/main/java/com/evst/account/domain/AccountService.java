package com.evst.account.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public interface AccountService {

    /**
     * Creates new account.
     *
     * @param id Identifier of the account being created
     * @param name Name of the account
     * @return future with the created account identifier
     */
    CompletionStage<UUID> createAccount(UUID id, String name) throws AccountAlreadyExistsException;

    /**
     * Look up existing account.
     *
     * @param id identifier of the account to look up
     * @return future with the found account
     * @throws AccountNotFoundException if no such account
     */
    CompletionStage<AccountState> retrieveAccount(UUID id) throws AccountNotFoundException;

    /**
     * Initiate transfer from one account to another.
     *
     * @param uniqueId unique identifier for the transfer to make the progress traceable
     * @param source identifier of the source account for transfer to transfer money from
     * @param target identifier of the target account for transfer transfer money to
     * @param amount amount of money to transfer
     * @return future with the created transfer
     *
     * @throws AccountNotFoundException if one of the accounts was not found
     * @throws TransferAlreadyExistsException if there is already created transfer with the same unique identifier
     * @throws TransferIsBeingCreatedException if a transfer with the same unique identifier is being created
     */
    CompletionStage<TransferState> makeTransfer(UUID uniqueId, UUID source, UUID target, BigDecimal amount)
        throws AccountNotFoundException, TransferAlreadyExistsException, TransferIsBeingCreatedException;

    /**
     * Look up existing transfer.
     *
     * @param id identifier of the transfer to look up
     * @return future with the found transfer
     * @throws TransferNotFoundException if no such transfer
     */
    CompletionStage<TransferState> retrieveTransfer(UUID id) throws TransferNotFoundException;

    /**
     * Deposit money of the given amount to target account.
     *
     * @param uniqueId unique identifier for the transfer to make the progress traceable
     * @param target identifier of the target account for transfer transfer money to
     * @param amount amount of money to transfer
     * @return future with the created transfer
     *
     * @throws AccountNotFoundException if one of the accounts was not found
     * @throws TransferAlreadyExistsException if there is already created transfer with the same unique identifier
     * @throws TransferIsBeingCreatedException if a transfer with the same unique identifier is being created
     */
    CompletionStage<TransferState> depositMoney(UUID uniqueId, UUID target, BigDecimal amount)
        throws AccountNotFoundException, TransferAlreadyExistsException, TransferIsBeingCreatedException;

    @Getter
    @AllArgsConstructor
    abstract class AccountServiceException extends RuntimeException {
        private final UUID id;
        private final String message;
    }

    /**
     * Indicates that requested to create account exists already.
     */
    final class AccountAlreadyExistsException extends AccountServiceException {
        AccountAlreadyExistsException(UUID id, String message) {
            super(id, message);
        }
    }

    /**
     * Indicates that requested account was not found.
     */
    final class AccountNotFoundException extends AccountServiceException {
        AccountNotFoundException(UUID id, String message) {
            super(id, message);
        }
    }

    /**
     * Indicates that transfer with the given unique identifier has been already created.
     */
    final class TransferAlreadyExistsException extends AccountServiceException {
        TransferAlreadyExistsException(UUID id, String message) {
            super(id, message);
        }
    }

    /**
     * Indicates that transfer with the given unique identifier is being created.
     */
    final class TransferIsBeingCreatedException extends AccountServiceException {
        TransferIsBeingCreatedException(UUID id, String message) {
            super(id, message);
        }
    }

    /**
     * Indicates that requested transfer was not found.
     */
    final class TransferNotFoundException extends AccountServiceException {
        TransferNotFoundException(UUID id, String message) {
            super(id, message);
        }
    }

}
