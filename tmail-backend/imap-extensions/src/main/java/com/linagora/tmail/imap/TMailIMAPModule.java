package com.linagora.tmail.imap;

import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.processor.NamespaceSupplier;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class TMailIMAPModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NamespaceSupplier.class).to(TMailNamespaceSupplier.class).in(Scopes.SINGLETON);
        bind(PathConverter.Factory.class).to(TMailPathConverter.Factory.class).in(Scopes.SINGLETON);
    }
}
