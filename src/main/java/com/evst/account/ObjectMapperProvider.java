package com.evst.account;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Provider;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ObjectMapperProvider implements Provider<ObjectMapper> {
    @Override
    public ObjectMapper get() {
        return new ObjectMapper()
            .disable(WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .disable(FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
