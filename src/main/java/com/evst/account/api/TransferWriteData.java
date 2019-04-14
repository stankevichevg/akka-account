package com.evst.account.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
@Getter
@NoArgsConstructor
public class TransferWriteData {

    @JsonProperty("transfer_id")
    private UUID transferId;

    @JsonProperty("source_account_id")
    private UUID sourceAccountId;

    @JsonProperty("target_account_id")
    private UUID targetAccountId;

    @JsonProperty("amount")
    private BigDecimal amount;

}
