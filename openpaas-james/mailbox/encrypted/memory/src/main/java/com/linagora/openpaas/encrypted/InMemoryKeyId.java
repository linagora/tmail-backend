package com.linagora.openpaas.encrypted;

import java.util.UUID;

public class InMemoryKeyId extends KeyId {

    public InMemoryKeyId(UUID id) {
        super(id);
    }

    public static InMemoryKeyId of(UUID value) {
        return new InMemoryKeyId(value);
    }
}
