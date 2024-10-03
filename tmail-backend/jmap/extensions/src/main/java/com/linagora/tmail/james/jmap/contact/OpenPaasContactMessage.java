package com.linagora.tmail.james.jmap.contact;

import java.util.Objects;

public class OpenPaasContactMessage {
    private String username;
    private String firstname;
    private String lastname;
    private String email;

    public OpenPaasContactMessage(String username, String firstname, String lastname, String email) {
        this.username = username;
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OpenPaasContactMessage that)) {
            return false;
        }
        return Objects.equals(username, that.username) &&
               Objects.equals(firstname, that.firstname) &&
               Objects.equals(lastname, that.lastname) &&
               Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, firstname, lastname, email);
    }
}
