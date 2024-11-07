package com.linagora.tmail;

import com.google.common.base.Preconditions;

public record Exchange(String name) {

    public Exchange {
        Preconditions.checkNotNull(name);
    }

    public static Exchange of(String name) {
        return new Exchange(name);
    }

    public boolean isDefaultExchange() {
        return name.isBlank();
    }
}
