package com.evst.account.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AccountReadData {

    @JsonProperty("account_id")
    private UUID id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("balance")
    private BigDecimal balance;

}
