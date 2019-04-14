package com.evst.account.domain;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import com.evst.account.domain.Message.AccountSnapshotResponse;
import com.evst.account.domain.Message.RetrieveAccountCommand;
import com.evst.account.domain.Message.RetrieveTransferCommand;
import com.evst.account.domain.TransferProtocolMessage.TransferHasAlreadyStarted;
import com.evst.account.domain.TransferProtocolMessage.TransferReadyCheck;
import com.evst.account.domain.TransferProtocolMessage.TransferReadyToStart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static com.evst.account.domain.TransferState.Status.COMPLETED;
import static com.evst.account.domain.TransferState.Status.LOW_BALANCE;
import static com.typesafe.config.ConfigValueFactory.fromAnyRef;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class TransferTest extends AbstractPersistedActorTest {

    private static UUID sourceAccountId;
    private static UUID targetAccountId;
    private static UUID transferId;

    private static ActorRef sourceAccountRef;
    private static ActorRef targetAccountRef;
    private static ActorRef transferRef;

    private static BigDecimal initialSourceBalance;
    private static BigDecimal transferAmount;

    @BeforeEach
    public void setup() throws IOException {

        super.setup();
        config = config.withValue("actor.pathprefix.account", fromAnyRef("/user/"));
        system = ActorSystem.create("test", config);

        new TestKit(system) {
            {
                sourceAccountId = UUID.randomUUID();
                initialSourceBalance = new BigDecimal(100.0);
                sourceAccountRef = system.actorOf(
                    Props.create(Account.class, sourceAccountId, initialSourceBalance), sourceAccountId.toString()
                );

                targetAccountId = UUID.randomUUID();
                targetAccountRef = system.actorOf(
                    Props.create(Account.class, targetAccountId), targetAccountId.toString()
                );
                targetAccountRef.tell(new Message.CreateAccountCommand(targetAccountId, "target"), getRef());
                expectMsg(new Message.AccountCreatedResponse(targetAccountId));

                transferId = UUID.randomUUID();
                transferAmount = new BigDecimal(50.0);
                transferRef = system.actorOf(Props.create(Transfer.class, transferId));

                transferRef.tell(
                    new Message.MakeTransferCommand(transferId, sourceAccountId, targetAccountId, transferAmount),
                    getRef()
                );

                final Message.TransferResponse response = expectMsgClass(Message.TransferResponse.class);
                assertThat(response.getTransferState().getStatus()).isEqualByComparingTo(COMPLETED);
                assertThat(response.getTransferState().getTargetAccountId()).isEqualByComparingTo(targetAccountId);
                assertThat(response.getTransferState().getSourceAccountId()).isEqualByComparingTo(sourceAccountId);
                assertThat(response.getTransferState().getAmount()).isEqualByComparingTo(transferAmount);
                assertThat(response.getTransferState().getId()).isEqualByComparingTo(transferId);
            }
        };
    }

    @Test
    public void whenNewTransferThenItIsReadyToStart() {
        new TestKit(system) {
            {
                final UUID transferId = UUID.randomUUID();
                final ActorRef accountRef = system.actorOf(Props.create(Transfer.class, transferId));

                accountRef.tell(new TransferReadyCheck(transferId), getRef());
                expectMsg(new TransferReadyToStart(transferId));
            }
        };
    }

    @Test
    public void whenTransferCompletedThenBalancesCorrect() {
        new TestKit(system) {
            {
                targetAccountRef.tell(new RetrieveAccountCommand(targetAccountId), getRef());
                final AccountSnapshotResponse targetResponse = expectMsgClass(AccountSnapshotResponse.class);
                assertThat(targetResponse.getAccount().getBalance()).isEqualTo(transferAmount);

                sourceAccountRef.tell(new RetrieveAccountCommand(sourceAccountId), getRef());
                final AccountSnapshotResponse sourceResponse = expectMsgClass(AccountSnapshotResponse.class);
                assertThat(sourceResponse.getAccount().getBalance()).isEqualTo(initialSourceBalance.subtract(transferAmount));
            }
        };
    }

    @Test
    public void whenTransferCompletedThenPossibleToRetrieveTheState() {
        new TestKit(system) {
            {
                transferRef.tell(new RetrieveTransferCommand(transferId), getRef());
                final Message.TransferSnapshotResponse resp = expectMsgClass(Message.TransferSnapshotResponse.class);
                assertThat(resp.getTransferState().getStatus()).isEqualByComparingTo(COMPLETED);
                assertThat(resp.getTransferState().getTargetAccountId()).isEqualByComparingTo(targetAccountId);
                assertThat(resp.getTransferState().getSourceAccountId()).isEqualByComparingTo(sourceAccountId);
                assertThat(resp.getTransferState().getAmount()).isEqualByComparingTo(transferAmount);
                assertThat(resp.getTransferState().getId()).isEqualByComparingTo(transferId);
            }
        };
    }

    @Test
    public void whenTransferCompletedThenItCanNotBeStartedAgain() {
        new TestKit(system) {
            {
                transferRef.tell(
                    new Message.MakeTransferCommand(transferId, sourceAccountId, targetAccountId, transferAmount),
                    getRef()
                );
                expectMsg(new TransferHasAlreadyStarted(transferId));
            }
        };
    }

    @Test
    public void whenTransferIsNotStartedThenItCaNotBeFound() {
        new TestKit(system) {
            {
                final UUID transferId = UUID.randomUUID();
                final BigDecimal transferAmount = new BigDecimal(50.0);
                final ActorRef transferRef = system.actorOf(Props.create(Transfer.class, transferId));

                transferRef.tell(new RetrieveTransferCommand(transferId), getRef());
                expectMsg(new Message.TransferNotFoundResponse(transferId));
            }
        };
    }

    @Test
    public void whenTransferMoreThanSourceHaveThenInsufficientBalanceError() {
        new TestKit(system) {
            {
                final UUID id = UUID.randomUUID();
                final BigDecimal amount = new BigDecimal(100500.0);
                final ActorRef transfer = system.actorOf(Props.create(Transfer.class, id));

                transfer.tell(new Message.MakeTransferCommand(id, sourceAccountId, targetAccountId, amount), getRef());
                final Message.TransferResponse response = expectMsgClass(Message.TransferResponse.class);
                assertThat(response.getTransferState().getStatus()).isEqualByComparingTo(LOW_BALANCE);
            }
        };
    }

}
