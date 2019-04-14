package com.evst.account.domain;

import com.evst.account.domain.TransferState.MoneyBlockFailed;
import com.evst.account.domain.TransferState.MoneyBlocked;
import com.evst.account.domain.TransferState.MoneyDeposited;
import com.evst.account.domain.TransferState.TransferCompleted;
import com.evst.account.domain.TransferState.TransferStarted;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.evst.account.domain.TransferState.Status.COMPLETED;
import static com.evst.account.domain.TransferState.Status.IN_PROGRESS;
import static com.evst.account.domain.TransferState.Status.LOW_BALANCE;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class TransferStateTest {

    private static TransferState state = null;

    @BeforeAll
    public static void init() {
        state = new TransferState(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(100.0), IN_PROGRESS, null
        );
    }

    @Test
    public void whenTransferStartedEventThenNewTransferStarted() {
        final UUID id = UUID.randomUUID();
        final UUID sourceId = UUID.randomUUID();
        final UUID targetId = UUID.randomUUID();
        final BigDecimal transfer = new BigDecimal(30.0);
        final TransferStarted started = new TransferStarted(id, sourceId, targetId, transfer);
        final TransferState startedState = started.mutate(null, null);

        assertThat(startedState.getId()).isEqualByComparingTo(id);
        assertThat(startedState.getSourceAccountId()).isEqualByComparingTo(sourceId);
        assertThat(startedState.getTargetAccountId()).isEqualByComparingTo(targetId);
        assertThat(startedState.getStatus()).isEqualByComparingTo(IN_PROGRESS);
        AssertionsForClassTypes.assertThat(startedState.getDeliverySnapshot()).isNull();

        AssertionsForClassTypes.assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            started.mutate(state, null)
        );
    }

    @Test
    public void whenMoneyBlockedThenProduceTheSameState() {
        final MoneyBlocked blocked = new MoneyBlocked(0L);
        final TransferState blockedState = blocked.mutate(state, null);

        assertThat(blockedState).isEqualTo(state);
    }

    @Test
    public void whenMoneyDepositedThenProduceTheSameState() {
        final MoneyDeposited deposited = new MoneyDeposited(0L);
        final TransferState depositedState = deposited.mutate(state, null);

        assertThat(depositedState).isEqualTo(state);
    }

    @Test
    public void whenMoneyBlockFailedThenChangeStateStatus() {
        final MoneyBlockFailed blockFailed = new MoneyBlockFailed(0L, LOW_BALANCE);
        final TransferState blockFailedState = blockFailed.mutate(state, null);

        assertThat(blockFailedState.getStatus()).isEqualTo(LOW_BALANCE);
    }

    @Test
    public void whenTransferCompletedThenChangeStateStatus() {
        final TransferCompleted completed = new TransferCompleted(0L);
        final TransferState completedState = completed.mutate(state, null);

        assertThat(completedState.getStatus()).isEqualTo(COMPLETED);
    }

    @Test
    public void whenStatusCodeRequestedThenReturnSpecifiedValues() {
        assertThat(IN_PROGRESS.getCode()).isEqualTo("in_progress");
        assertThat(COMPLETED.getCode()).isEqualTo("completed");
        assertThat(LOW_BALANCE.getCode()).isEqualTo("low_balance");
    }

    @Test
    public void whenNotificationCreatedThenDeliveryIdReturnedByGetter() {
        final Long deliveryId = 100500L;
        final MoneyBlockFailed blockFailed = new MoneyBlockFailed(deliveryId, LOW_BALANCE);
        final TransferCompleted completed = new TransferCompleted(deliveryId);
        final MoneyDeposited deposited = new MoneyDeposited(deliveryId);
        final MoneyBlocked blocked = new MoneyBlocked(deliveryId);

        assertThat(blockFailed.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(completed.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(deposited.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(blocked.getDeliveryId()).isEqualTo(deliveryId);
    }

}
