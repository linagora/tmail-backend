package com.linagora.tmail.webadmin;

import org.apache.james.webadmin.Routes;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class EmailAddressContactRoutesModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<Routes> routesMultibinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesMultibinder.addBinding().to(EmailAddressContactRoutes.class);
    }
}
