package com.linagora.tmail.imap;

import jakarta.inject.Inject;

import org.apache.james.imap.decode.parser.AuthenticateCommandParser;
import org.apache.james.imap.decode.parser.LoginCommandParser;
import org.apache.james.modules.protocols.ImapPackage;
import org.apache.james.utils.ClassName;

import com.google.common.collect.ImmutableList;

public class TMailImapAuthPackage extends ImapPackage.Impl {

    @Inject
    public TMailImapAuthPackage() {
        super(ImmutableList.of(new ClassName(TMailLoginProcessor.class.getCanonicalName()),
                new ClassName(TMailAuthenticateProcessor.class.getCanonicalName())),
            ImmutableList.of(new ClassName(LoginCommandParser.class.getCanonicalName()),
                new ClassName(AuthenticateCommandParser.class.getCanonicalName())),
            ImmutableList.of());
    }
}