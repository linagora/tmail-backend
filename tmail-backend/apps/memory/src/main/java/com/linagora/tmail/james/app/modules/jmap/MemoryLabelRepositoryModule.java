package com.linagora.tmail.james.app.modules.jmap;

import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.label.LabelChangeRepository;
import com.linagora.tmail.james.jmap.label.LabelRepository;
import com.linagora.tmail.james.jmap.label.LabelUserDeletionTaskStep;
import com.linagora.tmail.james.jmap.label.LabelUsernameChangeTaskStep;
import com.linagora.tmail.james.jmap.label.MemoryLabelChangeRepository;
import com.linagora.tmail.james.jmap.label.MemoryLabelRepository;

public class MemoryLabelRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(LabelRepository.class).to(MemoryLabelRepository.class);
        bind(MemoryLabelRepository.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class)
            .addBinding()
            .to(LabelUsernameChangeTaskStep.class);

        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class)
            .addBinding()
            .to(LabelUserDeletionTaskStep.class);

        bind(LabelChangeRepository.class).to(MemoryLabelChangeRepository.class);
        bind(MemoryLabelChangeRepository.class).in(Scopes.SINGLETON);
    }
}
