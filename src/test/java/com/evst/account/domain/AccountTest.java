package com.evst.account.domain;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.DebugFilter;
import akka.testkit.ErrorFilter;
import akka.testkit.javadsl.TestKit;
import com.evst.account.domain.Message.AccountNotFoundResponse;
import com.evst.account.domain.Message.AccountSnapshotResponse;
import com.evst.account.domain.Message.RetrieveAccountCommand;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class AccountTest extends AbstractPersistedActorTest {

    @Test
    public void successfulMoneyDepositProtocolPath() {
        new TestKit(system) {
            {
                final UUID accountId = UUID.randomUUID();
                final String name = "test";
                final ActorRef accountRef = system.actorOf(Props.create(Account.class, accountId));

                accountRef.tell(new Message.CreateAccountCommand(accountId, name), getRef());
                expectMsg(new Message.AccountCreatedResponse(accountId));

                accountRef.tell(new Message.CreateAccountCommand(accountId, name), getRef());
                expectMsg(new Message.AccountAlreadyExistsResponse(accountId));

                final UUID transferId = UUID.randomUUID();
                final Long depositDeliveryId = 0L;

                accountRef.tell(new TransferReadyCheck(transferId), getRef());
                expectMsg(new AccountReadyForTransfer(transferId, accountId));

                final DepositMoney depositMoney = new DepositMoney(depositDeliveryId, transferId, accountId, new BigDecimal(30.0));

                accountRef.tell(depositMoney, getRef());
                expectMsg(new MoneyDepositedSuccessfully(depositDeliveryId));

                // check that it reacts correctly for resent message
                accountRef.tell(depositMoney, getRef());
                expectMsg(new MoneyDepositedSuccessfully(depositDeliveryId));

                // TODO query event log to check events are saved
            }
        };
    }

    @Test
    public void successfulMoneyWithdrawProtocolPath() {
        new TestKit(system) {
            {
                final UUID accountId = UUID.randomUUID();
                final String name = "test";
                final ActorRef accountRef = system.actorOf(Props.create(Account.class, accountId, new BigDecimal(100.0)));

                final UUID transferId = UUID.randomUUID();
                final Long blockDeliveryId = 0L;
                final Long completeTransferDeliveryId = 0L;

                accountRef.tell(new TransferReadyCheck(transferId), getRef());
                expectMsg(new AccountReadyForTransfer(transferId, accountId));

                final BlockMoney blockMoney = new BlockMoney(blockDeliveryId, transferId, accountId, new BigDecimal(50.0));

                accountRef.tell(blockMoney, getRef());
                expectMsg(new MoneyBlockedSuccessfully(blockDeliveryId));

                // check that it reacts correctly for resent message
                accountRef.tell(blockMoney, getRef());
                expectMsg(new MoneyBlockedSuccessfully(blockDeliveryId));

                final CompleteTransfer complete = new CompleteTransfer(completeTransferDeliveryId, transferId);

                accountRef.tell(complete, getRef());
                expectMsg(new TransferCompletedSuccessfully(completeTransferDeliveryId));

                // check that it reacts correctly for resent message
                accountRef.tell(complete, getRef());
                expectMsg(new TransferCompletedSuccessfully(completeTransferDeliveryId));
            }
        };
    }

    @Test
    public void whenInsufficientMoneyThenBlockMoneyPathBroken() {
        new TestKit(system) {
            {
                final UUID accountId = UUID.randomUUID();
                final String name = "test";
                final ActorRef accountRef = system.actorOf(Props.create(Account.class, accountId, new BigDecimal(100.0)));

                final UUID transferId = UUID.randomUUID();
                final Long blockDeliveryId = 0L;
                final Long completeTransferDeliveryId = 0L;

                accountRef.tell(new TransferReadyCheck(transferId), getRef());
                expectMsg(new AccountReadyForTransfer(transferId, accountId));

                accountRef.tell(new BlockMoney(blockDeliveryId, transferId, accountId, new BigDecimal(150.0)), getRef());
                expectMsg(new InsufficientBalanceToBlock(blockDeliveryId));
            }
        };
    }

    @Test
    public void whenAccountNotExistsThenImpossibleToRetrieveIt() {
        new TestKit(system) {
            {
                final UUID accountId = UUID.randomUUID();
                final ActorRef accountRef = system.actorOf(Props.create(Account.class, accountId));

                accountRef.tell(new RetrieveAccountCommand(accountId), getRef());
                expectMsg(new AccountNotFoundResponse(accountId));
            }
        };
    }

    @Test
    public void whenAccountExistsThenRetrieveOperationIsPossible() {
        new TestKit(system) {
            {
                final UUID accountId = UUID.randomUUID();
                final String name = "test";
                final ActorRef accountRef = system.actorOf(Props.create(Account.class, accountId));

                accountRef.tell(new Message.CreateAccountCommand(accountId, name), getRef());
                expectMsg(new Message.AccountCreatedResponse(accountId));

                accountRef.tell(new RetrieveAccountCommand(accountId), getRef());
                final AccountSnapshotResponse response = expectMsgClass(AccountSnapshotResponse.class);
                assertThat(response.getAccount().getId()).isEqualByComparingTo(accountId);
                assertThat(response.getAccount().getName()).isEqualTo(name);
                assertThat(response.getAccount().getBalance()).isEqualTo(new BigDecimal(0.0));
            }
        };
    }

    @Test
    public void whenAccountNotExistsThenItIsNotReadyForTransfer() {
        new TestKit(system) {
            {
                final UUID accountId = UUID.randomUUID();
                final UUID transferId = UUID.randomUUID();
                final ActorRef accountRef = system.actorOf(Props.create(Account.class, accountId));

                accountRef.tell(new TransferReadyCheck(transferId), getRef());
                expectMsg(new AccountNotFoundForTransfer(transferId, accountId));
            }
        };
    }

    @Test
    public void whenSnapshotSavedSuccessfullyThenThereIsDebugLog() {
        new TestKit(system) {
            {
                final UUID accountId = UUID.randomUUID();
                final String name = "test";

                assertThat(new DebugFilter(
                    null, String.format("Snapshot of the account %s has been saved", accountId.toString()), false, false, 1
                ).intercept(() -> {
                    final ActorRef accountRef = system.actorOf(Props.create(Account.class, accountId));
                    accountRef.tell(new Message.CreateAccountCommand(accountId, name), getRef());
                    return true;
                }, system)).isTrue();
            }
        };
    }

    @Test
    public void testSnapshotCanNotBeSaved() {
        new TestKit(system) {
            {
                final UUID accountId = UUID.randomUUID();
                final String name = "test";
                try {
                    snapshot.setWritable(false);
                    assertThat(new ErrorFilter(
                        Throwable.class, null,
                        String.format("Snapshot of the account %s has not been saved", accountId.toString()),
                        false, false, 1
                    ).intercept(() -> {
                        final ActorRef accountRef = system.actorOf(Props.create(Account.class, accountId));
                        accountRef.tell(new Message.CreateAccountCommand(accountId, name), getRef());
                        return true;
                    }, system)).isTrue();
                } finally {
                    snapshot.setWritable(true);
                }
            }
        };
    }

}
