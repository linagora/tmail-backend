package com.linagora.tmail.imap;

import jakarta.inject.Inject;

import org.apache.james.imap.decode.parser.LoginCommandParser;
import org.apache.james.modules.protocols.ImapPackage;
import org.apache.james.utils.ClassName;

import com.google.common.collect.ImmutableList;

public class TmailImapLoginPackage extends ImapPackage.Impl {

    @Inject
    public TmailImapLoginPackage() {
        super(ImmutableList.of(new ClassName(TMailLoginProcessor.class.getCanonicalName())),
            ImmutableList.of(new ClassName(LoginCommandParser.class.getCanonicalName())),
            ImmutableList.of());
    }
}