package com.evst.account.domain;

import akka.persistence.AbstractPersistentActorWithAtLeastOnceDelivery;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
import com.evst.account.domain.TransferProtocolMessage.AccountNotFoundForTransfer;
import com.evst.account.domain.TransferProtocolMessage.AccountReadyForTransfer;
import com.evst.account.domain.TransferProtocolMessage.BlockMoney;
import com.evst.account.domain.TransferProtocolMessage.CompleteTransfer;
import com.evst.account.domain.TransferProtocolMessage.DepositMoney;
import com.evst.account.domain.TransferProtocolMessage.InsufficientBalanceToBlock;
import com.evst.account.domain.TransferProtocolMessage.MoneyBlockedSuccessfully;
import com.evst.account.domain.TransferProtocolMessage.MoneyDepositedSuccessfully;
import com.evst.account.domain.TransferProtocolMessage.TransferCompletedSuccessfully;
import com.evst.account.domain.TransferProtocolMessage.TransferReadyCheck;
import com.google.common.collect.ImmutableMap;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

/**
 * Account entity. Holds the current state of the account with the given identifier {@link #id}.
 *
 * If the state {@link #account} equals to <code>null</code> it can be considered as not existent account, internally
 * it can be checked using the {@link #isCreated()} method.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class Account extends AbstractPersistentActorWithAtLeastOnceDelivery {

    private static final String SAVE_SNAPSHOT_INTERVAL_SETTING = "akka.persistence.snapshot-store.interval.account";

    private final UUID id;

    private AccountState account;

    protected Account(UUID id) {
        this.id = id;
    }

    protected Account(UUID id, BigDecimal amount) {
        this.id = id;
        final Date now = new Date();
        account = new AccountState(id, "bank_temp_account", amount, now, now, ImmutableMap.of(), ImmutableMap.of());
    }

    @Override
    public String persistenceId() {
        return id.toString();
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
            .match(AccountState.Event.class, e -> account = e.mutate(this.account))
            .match(SnapshotOffer.class, ss -> account = (AccountState) ss.snapshot())
            .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Message.CreateAccountCommand.class, this::reactOn)
            .match(Message.RetrieveAccountCommand.class, this::reactOn)
            .match(TransferReadyCheck.class, this::reactOn)
            .match(BlockMoney.class, this::reactOn)
            .match(DepositMoney.class, this::reactOn)
            .match(CompleteTransfer.class, this::reactOn)
            .match(SaveSnapshotSuccess.class, this::reactOn)
            .match(SaveSnapshotFailure.class, this::reactOn)
            .build();
    }

    private void reactOn(TransferReadyCheck cmd) {
        if (account != null) {
            reply(new AccountReadyForTransfer(cmd.getTransferId(), id));
        } else {
            reply(new AccountNotFoundForTransfer(cmd.getTransferId(), id));
        }
    }

    private void reactOn(CompleteTransfer cmd) {
        if (account == null) {
            reply(new AccountNotFoundForTransfer(cmd.getTransferId(), id));
        } else if (account.hasWatchedCompletedTransfer(cmd.getTransferId())) {
            reply(new TransferCompletedSuccessfully(cmd.getDeliveryId()));
        } else {
            final AccountState.TransferCompleted event = new AccountState.TransferCompleted(
                cmd.getTransferId(), new Date()
            );
            persist(event, e -> {
                apply(e);
                reply(new TransferCompletedSuccessfully(cmd.getDeliveryId()));
            });
        }
    }

    private void reactOn(DepositMoney cmd) {
        if (account == null) {
            reply(new AccountNotFoundForTransfer(cmd.getTransferId(), id));
        } else {
            final AccountState.MoneyDeposited event = new AccountState.MoneyDeposited(
                cmd.getTransferId(), cmd.getSourceAccount(), cmd.getAmount(), new Date()
            );
            persist(event, e -> {
                apply(e);
                reply(new MoneyDepositedSuccessfully(cmd.getDeliveryId()));
            });
        }
    }

    private void reactOn(BlockMoney cmd) {
        if (account == null) {
            reply(new AccountNotFoundForTransfer(cmd.getTransferId(), id));
        } else if (account.hasActiveTransfer(cmd.getTransferId())) {
            reply(new MoneyBlockedSuccessfully(cmd.getDeliveryId()));
        } else if (!account.hasSufficientBalanceToBlock(cmd.getAmount())) {
            reply(new InsufficientBalanceToBlock(cmd.getDeliveryId()));
        } else {
            final AccountState.MoneyBlocked event = new AccountState.MoneyBlocked(
                cmd.getTransferId(), cmd.getTargetAccountId(), cmd.getAmount(), new Date()
            );
            persist(event, e -> {
                apply(e);
                reply(new MoneyBlockedSuccessfully(cmd.getDeliveryId()));
            });
        }
    }

    private void reactOn(Message.RetrieveAccountCommand cmd) {
        if (account == null) {
            reply(new Message.AccountNotFoundResponse(cmd.getId()));
        } else {
            reply(new Message.AccountSnapshotResponse(account));
        }
    }

    private void reactOn(Message.CreateAccountCommand cmd) {
        if (isCreated()) {
            reply(new Message.AccountAlreadyExistsResponse(cmd.getId()));
            return;
        }
        final AccountState.Created event = new AccountState.Created(cmd.getId(), cmd.getName(), new Date());
        persist(event, e -> {
            apply(e);
            reply(new Message.AccountCreatedResponse(cmd.getId()));
        });
    }

    private void reactOn(SaveSnapshotSuccess saveSnapshotSuccess) {
        getContext().getSystem().log().debug("Snapshot of the account {} has been saved", id);
    }

    private void reactOn(SaveSnapshotFailure saveSnapshotSuccess) {
        getContext().getSystem().log().error("Snapshot of the account {} has not been saved", id);
    }

    /**
     * Apply an event for the account state. It has to be the only point to change the state.
     * Each {@link #getSaveSnapshotInterval()} this method trigger {@link #saveSnapshot(Object)}
     * method to save current state.
     *
     * @param event an event to apply
     */
    private void apply(AccountState.Event event) {
        account = event.mutate(account);
        if (saveSnapshotTriggered()) {
            saveSnapshot(account);
        }
    }

    private boolean saveSnapshotTriggered() {
        return lastSequenceNr() % getSaveSnapshotInterval() == 0 && lastSequenceNr() != 0;
    }

    private boolean isCreated() {
        return account != null;
    }

    private void reply(Object message) {
        getSender().tell(message, getSelf());
    }

    private int getSaveSnapshotInterval() {
        return getContext().getSystem().settings().config().getInt(SAVE_SNAPSHOT_INTERVAL_SETTING);
    }

}
