package com.evst.account.domain;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.evst.account.domain.Message.AccountNotFoundResponse;
import com.evst.account.domain.Message.DepositMoneyCommand;
import com.evst.account.domain.Message.MakeTransferCommand;
import com.evst.account.domain.Message.TransferRequestIsBeingCreated;
import com.evst.account.domain.TransferProtocolMessage.AccountNotFoundForTransfer;
import com.evst.account.domain.TransferProtocolMessage.AccountReadyForTransfer;
import com.evst.account.domain.TransferProtocolMessage.TransferHasAlreadyStarted;
import com.evst.account.domain.TransferProtocolMessage.TransferReadyCheck;
import com.evst.account.domain.TransferProtocolMessage.TransferReadyToStart;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.UUID.randomUUID;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class AccountManager extends AbstractActor {

    private static final int CANCEL_PENDING_REQUEST_TIMEOUT = 1;

    private Map<UUID, PendingTransferRequest> pendingTransferRequests = new HashMap<>();

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Message.CreateAccountCommand.class, cmd -> {
                getOrCreateAccount(cmd.getId()).forward(cmd, getContext());
            })
            .match(Message.RetrieveAccountCommand.class, cmd -> {
                getOrCreateAccount(cmd.getId()).forward(cmd, getContext());
            })
            .match(Message.RetrieveTransferCommand.class, cmd -> {
                getOrCreateTransfer(cmd.getId()).forward(cmd, getContext());
            })
            .match(MakeTransferCommand.class, this::reactOn)
            .match(DepositMoneyCommand.class, this::reactOn)
            .match(CancelPendingTransferRequest.class, cmd -> {
                finishPendingTransferRequest(cmd.transferId);
            })
            .match(AccountReadyForTransfer.class, this::reactOn)
            .match(TransferReadyToStart.class, this::reactOn)
            .match(AccountNotFoundForTransfer.class, this::reactOn)
            .match(TransferHasAlreadyStarted.class, this::reactOn)
            .build();
    }

    private void reactOn(DepositMoneyCommand cmd) {
        if (pendingTransferRequests.containsKey(cmd.getId())) {
            getSender().tell(new TransferRequestIsBeingCreated(cmd.getId()), getSelf());
        } else {
            final UUID tempAccountId = randomUUID();
            final ActorRef source = getContext().actorOf(
                Props.create(Account.class, tempAccountId, cmd.getAmount()), tempAccountId.toString()
            );
            final ActorRef target = getOrCreateAccount(cmd.getTargetAccountId());
            final ActorRef transfer = getOrCreateTransfer(cmd.getId());
            final PendingTransferRequest request = new PendingTransferRequest(
                new MakeTransferCommand(cmd.getId(), tempAccountId, cmd.getTargetAccountId(), cmd.getAmount()),
                getSender(), transfer
            );
            createPendingTransferRequest(request, source, target, transfer);
        }
    }

    private void reactOn(MakeTransferCommand cmd) {
        if (pendingTransferRequests.containsKey(cmd.getId())) {
            getSender().tell(new TransferRequestIsBeingCreated(cmd.getId()), getSelf());
        } else {
            final ActorRef source = getOrCreateAccount(cmd.getSourceAccountId());
            final ActorRef target = getOrCreateAccount(cmd.getTargetAccountId());
            final ActorRef transfer = getOrCreateTransfer(cmd.getId());
            final PendingTransferRequest request = new PendingTransferRequest(cmd, getSender(), transfer);
            createPendingTransferRequest(request, source, target, transfer);
        }
    }

    private void reactOn(AccountReadyForTransfer cmd) {
        if (pendingTransferRequests.containsKey(cmd.getTransferId())) {
            final PendingTransferRequest request = pendingTransferRequests.get(cmd.getTransferId());
            request.markReady(cmd.getAccountId());
            if (request.isReady()) {
                request.transfer.tell(request.command, request.sender);
                finishPendingTransferRequest(cmd.getTransferId());
            }
        }
    }

    private void reactOn(TransferReadyToStart cmd) {
        if (pendingTransferRequests.containsKey(cmd.getTransferId())) {
            final PendingTransferRequest request = pendingTransferRequests.get(cmd.getTransferId());
            request.markReady(cmd.getTransferId());
            if (request.isReady()) {
                request.transfer.tell(request.command, request.sender);
                finishPendingTransferRequest(cmd.getTransferId());
            }
        }
    }

    private void reactOn(AccountNotFoundForTransfer cmd) {
        if (pendingTransferRequests.containsKey(cmd.getTransferId())) {
            final PendingTransferRequest request = pendingTransferRequests.get(cmd.getTransferId());
            request.sender.tell(new AccountNotFoundResponse(cmd.getAccountId()), getSelf());
            finishPendingTransferRequest(cmd.getTransferId());
        }
    }

    private void reactOn(TransferHasAlreadyStarted cmd) {
        if (pendingTransferRequests.containsKey(cmd.getTransferId())) {
            final PendingTransferRequest request = pendingTransferRequests.get(cmd.getTransferId());
            request.sender.tell(new Message.TransferAlreadyExistsResponse(cmd.getTransferId()), getSelf());
            finishPendingTransferRequest(cmd.getTransferId());
        }
    }

    private void createPendingTransferRequest(PendingTransferRequest request, ActorRef source, ActorRef target, ActorRef transfer) {
        final MakeTransferCommand cmd = request.command;
        pendingTransferRequests.put(cmd.getId(), request);
        source.tell(new TransferReadyCheck(cmd.getId()), getSelf());
        target.tell(new TransferReadyCheck(cmd.getId()), getSelf());
        transfer.tell(new TransferReadyCheck(cmd.getId()), getSelf());
        getContext().getSystem().getScheduler().scheduleOnce(
            Duration.ofSeconds(CANCEL_PENDING_REQUEST_TIMEOUT),
            getSelf(),
            new CancelPendingTransferRequest(cmd.getId()),
            getContext().getSystem().dispatcher(),
            null
        );
    }

    private void finishPendingTransferRequest(UUID transferId) {
        if (pendingTransferRequests.remove(transferId) != null) {
            getContext().getSystem().log().debug("Transfer request has been finished id:{}", transferId);
        }
    }

    private ActorRef getOrCreateAccount(UUID id) {
        final String name = id.toString();
        return getContext().findChild(name).orElseGet(
            () -> getContext().actorOf(Props.create(Account.class, id), name)
        );
    }

    private ActorRef getOrCreateTransfer(UUID id) {
        final String name = id.toString();
        return getContext().findChild(name).orElseGet(
            () -> getContext().actorOf(Props.create(Transfer.class, id), name)
        );
    }

    private static class PendingTransferRequest {

        final MakeTransferCommand command;
        final ActorRef sender;
        final ActorRef transfer;
        final Map<UUID, Boolean> readiness = new HashMap<>();

        PendingTransferRequest(MakeTransferCommand command, ActorRef sender, ActorRef transfer) {
            this.command = command;
            this.sender = sender;
            this.transfer = transfer;
            readiness.put(command.getId(), false);
            readiness.put(command.getSourceAccountId(), false);
            readiness.put(command.getTargetAccountId(), false);
        }

        boolean isReady() {
            return readiness.values().stream().reduce(true, (a, b) -> a && b);
        }

        void markReady(UUID id) {
            readiness.put(id, true);
        }
    }

    private class CancelPendingTransferRequest {

        private UUID transferId;

        CancelPendingTransferRequest(UUID transferId) {
            this.transferId = transferId;
        }
    }
}
