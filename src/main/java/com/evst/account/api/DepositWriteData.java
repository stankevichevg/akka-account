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
public class DepositWriteData {

    @JsonProperty("transfer_id")
    private UUID transferId;

    @JsonProperty("amount")
    private BigDecimal amount;

}
