package com.linagora.tmail.contact;

import java.util.Optional;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;

@JsonDeserialize(using = JCardObjectDeserializer.class)
public record JCardObject(String fn, Optional<String> emailOpt) {

    public JCardObject {
        Preconditions.checkNotNull(fn);
        Preconditions.checkNotNull(emailOpt);
    }

    /**
     * Purpose: To specify the formatted text corresponding to the name of
     * the object the vCard represents.
     * <p>
     * Example: Mr. John Q. Public\, Esq.
     */
    @Override
    public String fn() {
        return fn;
    }

    /**
     * Purpose: To specify the electronic mail address for communication
     * with the object the vCard represents.
     * <p>
     * Example: jane_doe@example.com
     */
    @Override
    public Optional<String> emailOpt() {
        return emailOpt;
    }
}
