package com.linagora.tmail.mailet;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.linagora.tmail.mailet.OpenPaasAmqpForwardAttribute.*;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

public class IsOpenPaasRabbitMqSetup extends GenericMatcher {

    private final OpenPaasRabbitMQChannelPoolHolder openPaasRabbitMQChannelPoolHolder;

    @Inject
    public IsOpenPaasRabbitMqSetup(OpenPaasRabbitMQChannelPoolHolder openPaasRabbitMQChannelPoolHolder) {
        this.openPaasRabbitMQChannelPoolHolder = openPaasRabbitMQChannelPoolHolder;
    }
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (openPaasRabbitMQChannelPoolHolder.get().isPresent()) {
            return mail.getRecipients();
        }
        return Collections.emptyList();
    }

}
