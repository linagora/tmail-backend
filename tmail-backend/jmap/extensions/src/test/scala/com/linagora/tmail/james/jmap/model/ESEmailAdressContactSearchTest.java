package com.linagora.tmail.james.jmap.model;

public class ESEmailAdressContactSearchTest implements EmailAddressContactSearchEngineContract{
    @Override
    public EmailAddressContactSearchEngine testee() {
        return new ESEmailAddressContactSearchEngine();
    }

    @Override
    public void indexShouldReturnMatched() {
        EmailAddressContactSearchEngineContract.super.indexShouldReturnMatched();
    }

    @Override
    public void indexShouldReturnNoMatch() {
        EmailAddressContactSearchEngineContract.super.indexShouldReturnNoMatch();
    }

    @Override
    public void indexShouldReturnEmpty() {
        EmailAddressContactSearchEngineContract.super.indexShouldReturnEmpty();
    }

    @Override
    public void indexWithDifferentAccount() {
        EmailAddressContactSearchEngineContract.super.indexWithDifferentAccount();
    }
}
