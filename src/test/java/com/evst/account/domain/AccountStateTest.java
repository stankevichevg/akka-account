package com.evst.account.domain;

import com.evst.account.domain.AccountState.MoneyBlocked;
import com.evst.account.domain.AccountState.MoneyDeposited;
import com.evst.account.domain.AccountState.TransferCompleted;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.assertj.core.util.DateUtil.now;
import static org.assertj.core.util.DateUtil.yesterday;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class AccountStateTest {

    private static AccountState state = null;

    @BeforeAll
    public static void init() {
        final Date now = yesterday();
        state = new AccountState(
            UUID.randomUUID(), "test", new BigDecimal(100.0), now, now, ImmutableMap.of(), ImmutableMap.of()
        );
    }

    @Test
    public void whenApplyCreatedEventThenNewAccountCreated() {
        final UUID id = UUID.randomUUID();
        final Date now = now();
        final String name = "test";
        final AccountState state = new AccountState.Created(id, name, now).mutate(null);

        assertThat(state.getId()).isEqualByComparingTo(id);
        assertThat(state.getName()).isEqualTo(name);
        assertThat(state.getBalance()).isEqualTo(new BigDecimal(0.0));
        assertThat(state.getCurrentTransfers()).isEmpty();
        assertThat(state.getWatchedCompletedTransfers()).isEmpty();
        assertThat(state.getCreatedAt()).isEqualTo(now);
        assertThat(state.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    public void whenApplyCreatedEventOnNotNullStateThenThrowsIllegalArgumentException() {
        final UUID id = UUID.randomUUID();
        final Date now = now();
        final String name = "test";

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
            state.update(new AccountState.Created(id, name, now))
        ).withMessage("Create event can not be applied for already created account");
    }

    @Test
    public void whenIncorrectAmountToBlockThenFail() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            state.hasSufficientBalanceToBlock(null)
        );
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            state.hasSufficientBalanceToBlock(new BigDecimal(-1.0))
        );
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            state.hasSufficientBalanceToBlock(new BigDecimal(0.0))
        );
    }

    @Test
    public void whenAskedForBalanceSufficiencyThenCalculateCorrectly() {
        assertThat(state.hasSufficientBalanceToBlock(new BigDecimal(100.0))).isTrue();
        assertThat(state.hasSufficientBalanceToBlock(new BigDecimal(50.0))).isTrue();
        assertThat(state.hasSufficientBalanceToBlock(new BigDecimal(100.0).add(BigDecimal.valueOf(1, 15)))).isFalse();
    }

    @Test
    public void whenAskedForMoneyBlockThenBlockCreatesOrFailedIfNotValidated() {
        final UUID id = UUID.randomUUID();
        final Date now = now();

        final BigDecimal toBlock = new BigDecimal(30.0);
        final MoneyBlocked blocked = new MoneyBlocked(id, state.getId(), toBlock, now);
        final AccountState blockedState = state.update(blocked);

        assertThat(blockedState.hasActiveTransfer(id)).isTrue();
        assertThat(blockedState.hasCurrentTransfer(id)).isTrue();
        assertThat(blockedState.getBalance()).isEqualTo(state.getBalance().subtract(toBlock));
        assertThat(blockedState.getCreatedAt()).isEqualTo(state.getCreatedAt());
        assertThat(blockedState.getUpdatedAt()).isEqualTo(now);

        final BigDecimal toBlockInsufficient = new BigDecimal(130.0);
        final MoneyBlocked blockedInsufficient = new MoneyBlocked(id, state.getId(), toBlockInsufficient, now);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            state.update(blockedInsufficient)
        );
    }

    @Test
    public void whenAskedForMoneyDepositThenDepositOrFailedIfNotValidated() {
        final UUID id = UUID.randomUUID();
        final Date now = now();

        final BigDecimal toDeposit = new BigDecimal(30.0);
        final MoneyDeposited deposited = new MoneyDeposited(id, state.getId(), toDeposit, now);
        final AccountState depositedState = state.update(deposited);

        assertThat(depositedState.hasActiveTransfer(id)).isFalse();
        assertThat(depositedState.hasCurrentTransfer(id)).isFalse();
        assertThat(depositedState.getBalance()).isEqualTo(state.getBalance().add(toDeposit));
        assertThat(depositedState.getCreatedAt()).isEqualTo(state.getCreatedAt());
        assertThat(depositedState.getUpdatedAt()).isEqualTo(now);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            new MoneyDeposited(id, state.getId(), new BigDecimal(-30.0), now)
        );
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            new MoneyDeposited(id, state.getId(), new BigDecimal(0.0), now)
        );
    }

    @Test
    public void whenTransferCompletedThenTransferMovedToCompleted() {
        final UUID id = UUID.randomUUID();
        final Date yesterday = yesterday();

        final BigDecimal toBlock = new BigDecimal(30.0);
        final MoneyBlocked blocked = new MoneyBlocked(id, state.getId(), toBlock, yesterday);
        final AccountState blockedState = state.update(blocked);
        final AccountState completedState = blockedState.update(new TransferCompleted(id, now()));

        assertThat(completedState.hasCurrentTransfer(id)).isFalse();
        assertThat(completedState.hasWatchedCompletedTransfer(id)).isTrue();
    }

    @Test
    public void whenNotificationCreatedTranDeliveryIdReturnedByGetter() {
        final UUID id = UUID.randomUUID();
        final Date yesterday = yesterday();

        final BigDecimal toBlock = new BigDecimal(30.0);
        final MoneyBlocked blocked = new MoneyBlocked(id, state.getId(), toBlock, yesterday);
        final AccountState blockedState = state.update(blocked);
        final AccountState completedState = blockedState.update(new TransferCompleted(id, now()));

        assertThat(completedState.hasCurrentTransfer(id)).isFalse();
        assertThat(completedState.hasWatchedCompletedTransfer(id)).isTrue();
    }



}
