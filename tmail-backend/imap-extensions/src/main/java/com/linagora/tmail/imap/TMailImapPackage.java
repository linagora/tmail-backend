package com.linagora.tmail.imap;

import org.apache.james.modules.protocols.ImapPackage;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class TMailImapPackage extends ImapPackage.Impl {

    static final ImapPackage AGGREGATE = ImapPackage.and(ImmutableList.of(new DefaultNoAuth(), new TMailImapAuthPackage()));

    @Inject
    public TMailImapPackage() {
        super(AGGREGATE.processors()
                .stream().collect(ImmutableList.toImmutableList()),
            AGGREGATE.decoders()
                .stream().collect(ImmutableList.toImmutableList()),
            AGGREGATE.encoders()
                .stream().collect(ImmutableList.toImmutableList()));
    }
}
