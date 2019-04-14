package com.evst.account.domain;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toSet;

/**
 * Account state. The only way to change it is to apply events using the {@link #update(Event)} method.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
@AllArgsConstructor
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AccountState implements Serializable {

    private final UUID id;
    private final String name;
    private final BigDecimal balance;
    private final Date createdAt;
    private final Date updatedAt;

    private final Map<UUID, ActiveTransfer> currentTransfers;

    /**
     * Holds completed transactions before we make it sure that there are no any
     * active deliveries from the transfer actor. So it's safe to clean up this just
     * after transaction termination by sending an additional message from the transfer actor.
     */
    private final Map<UUID, ActiveTransfer> watchedCompletedTransfers;

    /**
     * Apply an {@link Event} to the state and produce the new one.
     *
     * @param event an event
     * @return new state after applying of the event
     */
    public AccountState update(Event event) {
        return event.mutate(this);
    }

    /**
     * Checks if the the account has active transfer with the given id.
     *
     * @param id transfer identifier
     * @return <code>true</code> if the transfer exists and <code>false</code> if not
     */
    public boolean hasCurrentTransfer(UUID id) {
        return currentTransfers.containsKey(id);
    }

    /**
     * Checks if the the account has active transfer with the given id.
     *
     * @param id transfer identifier
     * @return <code>true</code> if the transfer exists and <code>false</code> if not
     */
    public boolean hasActiveTransfer(UUID id) {
        return currentTransfers.containsKey(id) || watchedCompletedTransfers.containsKey(id);
    }

    /**
     * Checks if the the account has watched completed transfer with the given id.
     *
     * @param id transfer identifier
     * @return <code>true</code> if the transfer exists and <code>false</code> if not
     */
    public boolean hasWatchedCompletedTransfer(UUID id) {
        return watchedCompletedTransfers.containsKey(id);
    }

    /**
     * Check if the balance is sufficient to block given amount.
     *
     * @param amount Amount requested to block.
     * @return <code>true</code> if the the balance is sufficient and <code>false</code> if not
     * @throws IllegalArgumentException if
     */
    public boolean hasSufficientBalanceToBlock(BigDecimal amount) {
        checkArgument(amount != null && amount.compareTo(ZERO) > 0, "Amount have to be positive number");
        return balance.compareTo(amount) >= 0;
    }

    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class ActiveTransfer implements Serializable {
        private final UUID id;
        private final UUID sourceAccountId;
        private final UUID targetAccountId;
        private final BigDecimal amount;
    }

    public interface Event extends Serializable {

        /**
         * Mutate state applying the event. Produce the new state.
         *
         * @param state old state
         * @return state after applying the event.
         */
        AccountState mutate(AccountState state);

    }

    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    public static final class Created implements Event, Serializable {

        private final UUID id;
        private final String name;
        private final Date time;

        @Override
        public AccountState mutate(AccountState account) {
            if (account != null) {
                throw new IllegalStateException("Create event can not be applied for already created account");
            }
            return new AccountState(
                id, name, new BigDecimal(0.0), time, time, ImmutableMap.of(), ImmutableMap.of()
            );
        }
    }

    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    public static final class MoneyBlocked implements Event, Serializable {

        private final UUID transactionId;
        private final UUID targetAccount;
        private final BigDecimal amount;
        private final Date time;

        @Override
        public AccountState mutate(AccountState account) {
            checkArgument(account.hasSufficientBalanceToBlock(amount), "Insufficient balance");
            return new AccountState(
                account.id, account.name, account.balance.subtract(amount), account.createdAt, this.time,
                ImmutableMap.<UUID, ActiveTransfer>builder()
                    .putAll(account.currentTransfers)
                    .put(transactionId, new ActiveTransfer(transactionId, account.id, targetAccount, amount))
                    .build(),
                ImmutableMap.<UUID, ActiveTransfer>builder()
                    .putAll(account.watchedCompletedTransfers)
                    .build()
            );
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    public static final class MoneyDeposited implements Event, Serializable {

        private final UUID transactionId;
        private final UUID sourceAccountId;
        private final BigDecimal amount;
        private final Date time;

        public MoneyDeposited(UUID transactionId, UUID sourceAccountId, BigDecimal amount, Date time) {
            checkArgument(amount != null && amount.compareTo(ZERO) > 0, "Amount have to be positive");
            this.transactionId = transactionId;
            this.sourceAccountId = sourceAccountId;
            this.amount = amount;
            this.time = time;
        }

        @Override
        public AccountState mutate(AccountState account) {

            return new AccountState(
                account.id, account.name, account.balance.add(amount), account.createdAt, this.time,
                account.currentTransfers, account.watchedCompletedTransfers
            );
        }
    }

    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    public static final class TransferCompleted implements Event, Serializable {

        private final UUID transferId;
        private final Date time;

        @Override
        public AccountState mutate(AccountState account) {
            final ActiveTransfer trx = account.currentTransfers.get(transferId);
            return new AccountState(
                account.id, account.name, account.balance, account.createdAt, this.time,
                ImmutableMap.<UUID, ActiveTransfer>builder()
                    .putAll(account.currentTransfers
                        .entrySet().stream()
                        .filter(e -> !e.getKey().equals(transferId))
                        .collect(toSet())
                    ).build(),
                ImmutableMap.<UUID, ActiveTransfer>builder()
                    .putAll(account.watchedCompletedTransfers)
                    // Consider to not to hold completed transfers. TransferCompleted is the terminate event in the
                    // protocol, so to do clean up we have to be sure that akka guarantees delivery of the other message
                    // types "happens before" this one in ANY CASE. At the moment we just collect completed transfers
                    // to ensure safeness
                    .put(trx.id, trx)
                    .build()
            );
        }
    }
}
