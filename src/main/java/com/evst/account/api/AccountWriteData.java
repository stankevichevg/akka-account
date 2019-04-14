package com.evst.account.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountWriteData {

    @JsonProperty("account_id")
    private UUID id;

    @JsonProperty("name")
    private String name;

}
