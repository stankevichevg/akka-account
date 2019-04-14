package com.evst.account.domain;

import akka.persistence.AtLeastOnceDelivery.AtLeastOnceDeliverySnapshot;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

import static com.evst.account.domain.TransferState.Status.COMPLETED;
import static com.evst.account.domain.TransferState.Status.IN_PROGRESS;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents the state model of the transfer. It's immutable object,
 * the only way to get a new state is to apply an {@link Event} producing the next state.
 *
 * Transfer state also holds the state of the akka delivery snapshot {@link AtLeastOnceDeliverySnapshot}.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
@Getter
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class TransferState implements Serializable {

    private final UUID id;
    private final UUID sourceAccountId;
    private final UUID targetAccountId;
    private final BigDecimal amount;
    private final Status status;

    /**
     * Adds delivery state to resend pended messages after a reincarnation.
     */
    private final AtLeastOnceDeliverySnapshot deliverySnapshot;

    /**
     * Transfer status.
     */
    public enum Status {

        IN_PROGRESS("in_progress"),
        COMPLETED("completed"),
        LOW_BALANCE("low_balance");

        private String code;

        Status(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * Event that can change a transfer state. Used to replay event sourced transfer state.
     */
    interface Event extends Serializable {

        /**
         * Mutate state applying the event. Produce new state.
         *
         * @param state old state
         * @return state after applying the event.
         */
        TransferState mutate(TransferState state, AtLeastOnceDeliverySnapshot deliverySnapshot);

    }

    /**
     * Indicates that transfer has been started. Initialize the state of the just created transfer.
     */
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    static final class TransferStarted implements Event, Serializable {

        private UUID id;
        private UUID sourceAccountId;
        private UUID targetAccountId;
        private BigDecimal amount;

        @Override
        public TransferState mutate(TransferState state, AtLeastOnceDeliverySnapshot deliverySnapshot) {
            checkArgument(state == null, "Start event can not be applied for already started transfer");
            return new TransferState(id, sourceAccountId, targetAccountId, amount, IN_PROGRESS, deliverySnapshot);
        }
    }

    /**
     * Base notification from a account. Holds delivery id to support at least one delivery.
     */
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    abstract static class Notification implements Event, Serializable {

        @Getter private Long deliveryId;

        @Override
        public TransferState mutate(TransferState state, AtLeastOnceDeliverySnapshot deliverySnapshot) {
            return new TransferState(
                state.id, state.sourceAccountId, state.targetAccountId, state.amount,
                state.status, deliverySnapshot
            );
        }
    }

    /**
     * Notification about successfully blocked money from the source account of the transfer.
     */
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    static final class MoneyBlocked extends Notification {
        MoneyBlocked(Long deliveryId) {
            super(deliveryId);
        }
    }

    /**
     * Notification about failed money block operation from the source account of the transfer.
     */
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    static final class MoneyBlockFailed extends Notification {

        private Status status;

        MoneyBlockFailed(Long deliveryId, Status status) {
            super(deliveryId);
            this.status = status;
        }

        @Override
        public TransferState mutate(TransferState state, AtLeastOnceDeliverySnapshot deliverySnapshot) {
            return new TransferState(
                state.id, state.sourceAccountId, state.targetAccountId, state.amount,
                status, deliverySnapshot
            );
        }
    }

    /**
     * Notification about successful deposit operation from the target account of the transfer.
     */
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    static final class MoneyDeposited extends Notification {
        MoneyDeposited(Long deliveryId) {
            super(deliveryId);
        }
    }

    /**
     * Notification about successful deposit operation from the target account of the transfer.
     */
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    static final class TransferCompleted extends Notification {

        TransferCompleted(Long deliveryId) {
            super(deliveryId);
        }

        @Override
        public TransferState mutate(TransferState state, AtLeastOnceDeliverySnapshot deliverySnapshot) {
            return new TransferState(
                state.id, state.sourceAccountId, state.targetAccountId, state.amount,
                COMPLETED, deliverySnapshot
            );
        }
    }

}
