package com.linagora.openpaas.james.common.module;

import org.apache.james.utils.GuiceProbe;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.linagora.openpaas.james.common.probe.JmapGuiceCustomProbe;

public class JmapGuiceCustomModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), GuiceProbe.class)
            .addBinding()
            .to(JmapGuiceCustomProbe.class);
    }
}
