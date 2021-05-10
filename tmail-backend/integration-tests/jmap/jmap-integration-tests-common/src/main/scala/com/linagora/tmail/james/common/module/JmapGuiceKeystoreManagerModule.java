package com.linagora.tmail.james.common.module;

import org.apache.james.utils.GuiceProbe;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.common.probe.JmapGuiceKeystoreManagerProbe;

public class JmapGuiceKeystoreManagerModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), GuiceProbe.class)
            .addBinding()
            .to(JmapGuiceKeystoreManagerProbe.class);
    }
}
