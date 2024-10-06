package com.linagora.tmail.james.jmap.contact;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = jCardObjectDeserializer.class)
public record jCardObject(String fn, String email) {

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
    public String email() {
        return email;
    }
}
