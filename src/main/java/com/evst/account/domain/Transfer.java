package com.evst.account.domain;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.persistence.AbstractPersistentActorWithAtLeastOnceDelivery;
import akka.persistence.SnapshotOffer;
import com.evst.account.domain.Message.MakeTransferCommand;
import com.evst.account.domain.Message.RetrieveTransferCommand;
import com.evst.account.domain.Message.TransferNotFoundResponse;
import com.evst.account.domain.Message.TransferSnapshotResponse;
import com.evst.account.domain.TransferProtocolMessage.BlockMoney;
import com.evst.account.domain.TransferProtocolMessage.CompleteTransfer;
import com.evst.account.domain.TransferProtocolMessage.DepositMoney;
import com.evst.account.domain.TransferProtocolMessage.InsufficientBalanceToBlock;
import com.evst.account.domain.TransferProtocolMessage.MoneyBlockedSuccessfully;
import com.evst.account.domain.TransferProtocolMessage.MoneyDepositedSuccessfully;
import com.evst.account.domain.TransferProtocolMessage.TransferCompletedSuccessfully;
import com.evst.account.domain.TransferProtocolMessage.TransferHasAlreadyStarted;
import com.evst.account.domain.TransferProtocolMessage.TransferReadyCheck;
import com.evst.account.domain.TransferProtocolMessage.TransferReadyToStart;
import com.evst.account.domain.TransferState.Event;
import com.evst.account.domain.TransferState.MoneyBlockFailed;
import com.evst.account.domain.TransferState.MoneyBlocked;
import com.evst.account.domain.TransferState.MoneyDeposited;
import com.evst.account.domain.TransferState.TransferCompleted;
import com.evst.account.domain.TransferState.TransferStarted;

import java.util.UUID;

import static com.evst.account.domain.TransferState.Status.LOW_BALANCE;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class Transfer extends AbstractPersistentActorWithAtLeastOnceDelivery {

    private static final String ACCOUNT_ACTOR_PATH_SETTING = "actor.pathprefix.account";

    private UUID id;
    private TransferState transfer;
    private ActorRef initiator;

    public Transfer(UUID id) {
        this.id = id;
    }

    @Override
    public String persistenceId() {
        return id.toString();
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
            .match(Event.class, e -> transfer = e.mutate(this.transfer, getDeliverySnapshot()))
            .match(SnapshotOffer.class, ss -> transfer = (TransferState) ss.snapshot())
            .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(RetrieveTransferCommand.class, this::reactOn)
            .match(TransferReadyCheck.class, this::reactOn)
            .match(MakeTransferCommand.class, this::reactOn)
            .match(MoneyBlockedSuccessfully.class, this::reactOn)
            .match(InsufficientBalanceToBlock.class, this::reactOn)
            .match(MoneyDepositedSuccessfully.class, this::reactOn)
            .match(TransferCompletedSuccessfully.class, this::reactOn)
            .build();
    }

    private void reactOn(RetrieveTransferCommand retrieveTransferCommand) {
        if (transfer == null) {
            getSender().tell(new TransferNotFoundResponse(id), getSelf());
        } else {
            getSender().tell(new TransferSnapshotResponse(transfer), getSelf());
        }
    }

    private void reactOn(TransferReadyCheck transferReadyCheck) {
        if (transfer == null) {
            getSender().tell(new TransferReadyToStart(id), getSelf());
        } else {
            getSender().tell(new TransferHasAlreadyStarted(id), getSelf());
        }
    }

    private void reactOn(MoneyBlockedSuccessfully cmd) {
        persist(
            new MoneyBlocked(cmd.getDeliveryId()),
            event -> {
                updateState(event);
                confirmDelivery(event.getDeliveryId());
                deliver(toTargetAccount(), dId ->
                    new DepositMoney(dId, id, transfer.getSourceAccountId(), transfer.getAmount())
                );
            }
        );
    }

    private void reactOn(InsufficientBalanceToBlock cmd) {
        persist(
            new MoneyBlockFailed(cmd.getDeliveryId(), LOW_BALANCE),
            event -> {
                updateState(event);
                confirmDelivery(event.getDeliveryId());
                if (initiator != null) {
                    // if it's reincarnated TransferActor just let to fail with request timeout
                    initiator.tell(new Message.TransferResponse(transfer), getSelf());
                }
            }
        );
    }

    private void reactOn(MoneyDepositedSuccessfully cmd) {
        persist(
            new MoneyDeposited(cmd.getDeliveryId()),
            event -> {
                updateState(event);
                confirmDelivery(event.getDeliveryId());
                deliver(toSourceAccount(), dId ->
                    new CompleteTransfer(dId, transfer.getId())
                );
            }
        );
    }

    private void reactOn(MakeTransferCommand cmd) {
        if (transfer != null) {
            getSender().tell(new TransferHasAlreadyStarted(id), getSelf());
        } else {
            persist(
                new TransferStarted(
                    cmd.getId(), cmd.getSourceAccountId(), cmd.getTargetAccountId(), cmd.getAmount()
                ),
                event -> {
                    initiator = getSender();
                    updateState(event);
                    deliver(toSourceAccount(), dId ->
                        new BlockMoney(dId, cmd.getId(), cmd.getTargetAccountId(), cmd.getAmount())
                    );
                }
            );
        }
    }

    private void reactOn(TransferCompletedSuccessfully cmd) {
        persist(
            new TransferCompleted(cmd.getDeliveryId()),
            event -> {
                updateState(event);
                confirmDelivery(event.getDeliveryId());
                if (initiator != null) {
                    // if it's reincarnated TransferActor just let to fail with request timeout
                    initiator.tell(
                        new Message.TransferResponse(transfer),
                        getSelf()
                    );
                }
            }
        );
    }

    private ActorSelection toSourceAccount() {
        return toAccount(transfer.getSourceAccountId());
    }

    private ActorSelection toTargetAccount() {
        return toAccount(transfer.getTargetAccountId());
    }

    private void updateState(Event event) {
        transfer = event.mutate(transfer, getDeliverySnapshot());
    }

    private ActorSelection toAccount(UUID id) {
        return getContext().actorSelection(getAccountPathPrefix() + id.toString());
    }

    private String getAccountPathPrefix() {
        return getContext().getSystem().settings().config().getString(ACCOUNT_ACTOR_PATH_SETTING);
    }

}
