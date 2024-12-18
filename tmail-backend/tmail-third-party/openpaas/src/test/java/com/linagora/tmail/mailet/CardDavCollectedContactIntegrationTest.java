package com.linagora.tmail.mailet;

import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.api.OpenPaasServerExtension;
import com.linagora.tmail.carddav.CardDavServerExtension;

public class CardDavCollectedContactIntegrationTest {

    @RegisterExtension
    static OpenPaasServerExtension openPaasServerExtension = new OpenPaasServerExtension();

    @RegisterExtension
    static CardDavServerExtension cardDavServerExtension = new CardDavServerExtension();
}
