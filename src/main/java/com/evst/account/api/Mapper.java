package com.evst.account.api;

import com.evst.account.domain.AccountState;
import com.evst.account.domain.TransferState;

/**
 * Helper class to translate domain objects to read DTOs.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public final class Mapper {

    private Mapper() {
    }

    /**
     * Maps {@link AccountState} to {@link AccountReadData}.
     *
     * @param account account state to be mapped
     * @return DTO read object for account
     */
    public static AccountReadData accountData(AccountState account) {
        return new AccountReadData(account.getId(), account.getName(), account.getBalance());
    }

    /**
     * Maps {@link TransferState} to {@link TransferReadData}.
     *
     * @param transfer transfer state to be mapped
     * @return DTO read object for transfer
     */
    public static TransferReadData transferData(TransferState transfer) {
        return new TransferReadData(
            transfer.getId(), transfer.getSourceAccountId(), transfer.getTargetAccountId(),
            transfer.getAmount(), transfer.getStatus().getCode()
        );
    }

    /**
     * Maps {@link TransferState} to {@link DepositReadData}.
     *
     * @param transfer transfer state to be mapped
     * @return DTO read object for deposit
     */
    public static DepositReadData depositData(TransferState transfer) {
        return new DepositReadData(
            transfer.getId(), transfer.getTargetAccountId(), transfer.getAmount(), transfer.getStatus().getCode()
        );
    }
}
