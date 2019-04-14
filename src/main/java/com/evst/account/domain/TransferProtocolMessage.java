package com.evst.account.domain;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Messages of the transfer protocol used internally by the account management system.
 * It can not be considered as a part of the public interface.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
abstract class TransferProtocolMessage implements Serializable {

    /**
     * First message from the {@link AccountManager} to check readiness of all participants:
     * source and target accounts (check for existence) and transfer (check that has not been created yet).
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class TransferReadyCheck extends TransferProtocolMessage {
        private UUID transferId;
    }

    /**
     * Notification about readiness of the account,
     * sent as a reply to {@link TransferReadyCheck} message from transfer to account manager.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class AccountReadyForTransfer extends TransferProtocolMessage {
        private UUID transferId;
        private UUID accountId;
    }

    /**
     * Notification that account is not found,
     * sent as a reply to {@link TransferReadyCheck} message from transfer to account manager.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class AccountNotFoundForTransfer extends TransferProtocolMessage {
        private UUID transferId;
        private UUID accountId;
    }

    /**
     * Notification from transfer that it's ready to start,
     * sent as a reply to {@link TransferReadyCheck} message from transfer to account manager.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class TransferReadyToStart extends TransferProtocolMessage {
        private UUID transferId;
    }

    /**
     * Notification from transfer that it's has already started,
     * sent as a reply to {@link TransferReadyCheck} message from transfer to account manager.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class TransferHasAlreadyStarted extends TransferProtocolMessage {
        private UUID transferId;
    }

    /**
     * Command from the transfer to source account to block specified amount of money.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class BlockMoney extends TransferProtocolMessage {
        private Long deliveryId;
        private UUID transferId;
        private UUID targetAccountId;
        private BigDecimal amount;
    }

    /**
     * Notification from the source account that money has been blocked,
     * sent as a reply to {@link BlockMoney} message from source account to transfer.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class MoneyBlockedSuccessfully extends TransferProtocolMessage {
        private Long deliveryId;
    }

    /**
     * Notification from the source account that money can not be blocked because insufficient balance,
     * sent as a reply to {@link BlockMoney} message from source account to transfer.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class InsufficientBalanceToBlock extends TransferProtocolMessage {
        private Long deliveryId;
    }

    /**
     * Command from the transfer to target account to deposit specified amount of money.
     *
     * At the moment there is no other notifications to it instead of {@link MoneyDepositedSuccessfully}.
     * It's so for simplicity, but business logic can be supported here. For example to support blocked account
     * we can add compensation operation for source account to get blocked money back.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class DepositMoney extends TransferProtocolMessage {
        private Long deliveryId;
        private UUID transferId;
        private UUID sourceAccount;
        private BigDecimal amount;
    }

    /**
     * Notification from the source account that money has been deposited,
     * sent as a reply to {@link DepositMoney} message from target account to transfer.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class MoneyDepositedSuccessfully extends TransferProtocolMessage {
        private Long deliveryId;
    }

    /**
     * Command from the transfer to source account to complete transfer.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class CompleteTransfer extends TransferProtocolMessage {
        private Long deliveryId;
        private UUID transferId;
    }

    /**
     * Notification from the source account to the transfer about successfully completed transaction,
     * sent as a reply to {@link DepositMoney} message from target account to transfer.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    static class TransferCompletedSuccessfully extends TransferProtocolMessage {
        private Long deliveryId;
    }

}
