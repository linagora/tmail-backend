package com.linagora.tmail.rspamd;

import java.net.URL;
import java.util.Optional;

import org.apache.james.rspamd.RspamdExtension;
import org.apache.james.rspamd.client.RspamdClientConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class RspamdExtensionModule extends RspamdExtension {

    public static class TestRspamdModule extends AbstractModule {

        private final URL rspamdUrl;

        public TestRspamdModule(URL rspamdUrl) {
            this.rspamdUrl = rspamdUrl;
        }

        @Provides
        @Singleton
        public RspamdClientConfiguration rspamdClientConfiguration() {
            return new RspamdClientConfiguration(rspamdUrl, RspamdExtension.PASSWORD, Optional.empty());
        }
    }

    @Override
    public Module getModule() {
        return new TestRspamdModule(rspamdURL());
    }
}
