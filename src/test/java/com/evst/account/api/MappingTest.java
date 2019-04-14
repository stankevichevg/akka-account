package com.evst.account.api;

import com.evst.account.ObjectMapperProvider;
import com.evst.account.domain.AccountState;
import com.evst.account.domain.TransferState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

import static com.evst.account.Helpers.resourceAsString;
import static com.evst.account.domain.TransferState.Status.IN_PROGRESS;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.assertj.core.util.DateUtil.yesterday;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class MappingTest {

    private static AccountState accountState = null;
    private static TransferState transferState = null;
    private ObjectMapperProvider objectMapperProvider = new ObjectMapperProvider();

    @BeforeAll
    public static void init() {
        final Date now = yesterday();
        accountState = new AccountState(
            UUID.randomUUID(), "test", new BigDecimal(100.0), now, now, ImmutableMap.of(), ImmutableMap.of()
        );
        transferState = new TransferState(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(100.0), IN_PROGRESS, null
        );
    }

    @Test
    public void whenMapsTransferStateThenTransferReadDataReceivesRequiredValues() throws JsonProcessingException {
        final TransferReadData transferReadData = Mapper.transferData(transferState);
        assertThat(transferReadData.getTransferId()).isEqualTo(transferState.getId());
        assertThat(transferReadData.getSourceAccountId()).isEqualTo(transferState.getSourceAccountId());
        assertThat(transferReadData.getTargetAccountId()).isEqualTo(transferState.getTargetAccountId());
        assertThat(transferReadData.getStatus()).isEqualTo(transferState.getStatus().getCode());
        assertThat(transferReadData.getAmount()).isEqualTo(transferState.getAmount());

        final String expectedJSON = String.format(
            resourceAsString("com/evst/account/api/transfer_read_data.template"),
            transferReadData.getTransferId(),
            transferReadData.getSourceAccountId(),
            transferReadData.getTargetAccountId(),
            transferReadData.getAmount(),
            transferReadData.getStatus()
        );
        assertThat(objectMapperProvider.get().writeValueAsString(transferReadData)).isEqualTo(expectedJSON);
    }

    @Test
    public void whenMapsTransferStateThenDepositReadDataReceivesRequiredValues() throws JsonProcessingException {
        final DepositReadData depositReadData = Mapper.depositData(transferState);
        assertThat(depositReadData.getTransferId()).isEqualTo(transferState.getId());
        assertThat(depositReadData.getTargetAccountId()).isEqualTo(transferState.getTargetAccountId());
        assertThat(depositReadData.getStatus()).isEqualTo(transferState.getStatus().getCode());
        assertThat(depositReadData.getAmount()).isEqualTo(transferState.getAmount());

        final String expectedJSON = String.format(
            resourceAsString("com/evst/account/api/deposit_read_data.template"),
            depositReadData.getTransferId(),
            depositReadData.getTargetAccountId(),
            depositReadData.getAmount(),
            depositReadData.getStatus()
        );
        assertThat(objectMapperProvider.get().writeValueAsString(depositReadData)).isEqualTo(expectedJSON);
    }

    @Test
    public void whenMapsAccountStateThenAccountReadDataReceivesRequiredValues() {
        final AccountReadData accountReadData = Mapper.accountData(accountState);
        assertThat(accountReadData.getId()).isEqualTo(accountState.getId());
        assertThat(accountReadData.getName()).isEqualTo(accountState.getName());
        assertThat(accountReadData.getBalance()).isEqualTo(accountState.getBalance());
    }

    @Test
    public void whenUnmarshallAccountJsonThenAccountWriteDataReceivesRequiredValues() throws IOException {
        final String json = String.format(
            resourceAsString("com/evst/account/api/account_write_data.template"),
            accountState.getId(),
            accountState.getName()
        );
        final AccountWriteData data = objectMapperProvider.get().readValue(json, AccountWriteData.class);
        assertThat(data.getId()).isEqualByComparingTo(accountState.getId());
        assertThat(data.getName()).isEqualTo(accountState.getName());
    }

    @Test
    public void whenUnmarshallDepositJsonThenDepositWriteDataReceivesRequiredValues() throws IOException {
        final BigDecimal depositAmount = new BigDecimal(50.0);
        final String json = String.format(
            resourceAsString("com/evst/account/api/deposit_write_data.template"),
            accountState.getId(),
            depositAmount
        );
        final DepositWriteData data = objectMapperProvider.get().readValue(json, DepositWriteData.class);
        assertThat(data.getTransferId()).isEqualByComparingTo(accountState.getId());
        assertThat(data.getAmount()).isEqualTo(depositAmount);
    }

    @Test
    public void whenUnmarshallTransferJsonThenTransferWriteDataReceivesRequiredValues() throws IOException {
        final UUID transferId = UUID.randomUUID();
        final UUID sourceAccountId = UUID.randomUUID();
        final BigDecimal transferAmount = new BigDecimal(50.0);
        final String json = String.format(
            resourceAsString("com/evst/account/api/transfer_write_data.template"),
            transferId,
            sourceAccountId,
            accountState.getId(),
            transferAmount
        );
        final TransferWriteData data = objectMapperProvider.get().readValue(json, TransferWriteData.class);
        assertThat(data.getTransferId()).isEqualByComparingTo(transferId);
        assertThat(data.getSourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(data.getTargetAccountId()).isEqualTo(accountState.getId());
        assertThat(data.getAmount()).isEqualTo(transferAmount);
    }

}
