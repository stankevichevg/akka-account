package com.evst.account.domain;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Commands and responses of the account management system public interface.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public abstract class Message implements Serializable {

    /**
     * Command to the {@link AccountManager} to create a new account.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class CreateAccountCommand extends Message {
        private final UUID id;
        private final String name;
    }

    /**
     * Notification that account has already been created,
     * sent as a response to {@link CreateAccountCommand} message from the account to the initiator of the creation.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class AccountAlreadyExistsResponse extends Message {
        private final UUID id;
    }

    /**
     * Notification that account has been created successfully,
     * sent as a response to {@link CreateAccountCommand} message from the account to the initiator of the creation.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class AccountCreatedResponse extends Message {
        private final UUID id;
    }

    /**
     * Command to the {@link AccountManager} to retrieve the existing account.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class RetrieveAccountCommand extends Message {
        private final UUID id;
    }

    /**
     * Notification that account has not been found,
     * sent as a response to {@link RetrieveAccountCommand} message from the account to the requester.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class AccountNotFoundResponse extends Message {
        private final UUID id;
    }

    /**
     * Notification with the current account state,
     * sent as a response to {@link RetrieveAccountCommand} message from the account to the requester.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class AccountSnapshotResponse extends Message {
        private final AccountState account;
    }

    /**
     * Command to the {@link AccountManager} to retrieve the existing transfer.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class RetrieveTransferCommand extends Message {
        private final UUID id;
    }

    /**
     * Notification with the current transfer state,
     * sent as a response to {@link RetrieveTransferCommand} message from the transfer to the requester.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class TransferResponse extends Message {
        private final TransferState transferState;
    }

    /**
     * Notification that transfer has not been found,
     * sent as a response to {@link RetrieveTransferCommand} message from the transfer to the requester.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class TransferNotFoundResponse extends Message {
        private final UUID id;
    }

    /**
     * Notification with the current transfer state,
     * sent as a response to {@link RetrieveTransferCommand} message from the account to the requester.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class TransferSnapshotResponse extends Message {
        private final TransferState transferState;
    }

    /**
     * Command to the {@link AccountManager} to start a new transfer.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class MakeTransferCommand extends Message {
        private final UUID id;
        private final UUID sourceAccountId;
        private final UUID targetAccountId;
        private final BigDecimal amount;
    }

    /**
     * Notification that transfer has already been created,
     * sent as a response to {@link MakeTransferCommand} message from the transfer to the initiator of the creation.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class TransferAlreadyExistsResponse extends Message {
        private final UUID id;
    }

    /**
     * Command to the {@link AccountManager} to deposit money to the account.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class DepositMoneyCommand extends Message {
        private final UUID id;
        private final UUID targetAccountId;
        private final BigDecimal amount;
    }

    /**
     * Notification that transfer request is being created,
     * sent as a response to {@link MakeTransferCommand} message from the account manager to the initiator of the creation.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class TransferRequestIsBeingCreated extends Message {
        private final UUID id;
    }

}
