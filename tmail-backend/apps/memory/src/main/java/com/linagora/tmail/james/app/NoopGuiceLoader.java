/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/
package com.linagora.tmail.james.app;

import java.util.stream.Stream;

import org.apache.james.utils.ClassName;
import org.apache.james.utils.FullyQualifiedClassName;
import org.apache.james.utils.GuiceLoader;
import org.apache.james.utils.NamingScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

public class NoopGuiceLoader implements GuiceLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoopGuiceLoader.class);

    public static class InvocationPerformer<T> implements GuiceLoader.InvocationPerformer<T> {
        private final Injector injector;
        private NamingScheme namingScheme;

        private InvocationPerformer(Injector injector, NamingScheme namingScheme) {
            this.injector = injector;
            this.namingScheme = namingScheme;
        }

        @Override
        public T instantiate(ClassName className) throws ClassNotFoundException {
            try {
                Class<T> clazz = locateClass(className);
                return injector.getInstance(clazz);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load " + className.getName(), e);
            }
        }

        public Class<T> locateClass(ClassName className) throws ClassNotFoundException {
            ImmutableList<Class<T>> classes = this.namingScheme.toFullyQualifiedClassNames(className)
                .flatMap(this::tryLocateClass)
                .collect(ImmutableList.toImmutableList());

            if (classes.size() == 0) {
                throw new ClassNotFoundException(className.getName());
            }
            if (classes.size() > 1) {
                LOGGER.warn("Ambiguous class name for {}. Corresponding classes are {} and {} will be loaded",
                    className, classes, classes.get(0));
            }
            return classes.get(0);
        }

        private Stream<Class<T>> tryLocateClass(FullyQualifiedClassName className) {
            try {
                Class<?> clazz = getClass().getClassLoader().loadClass(className.getName());
                return Stream.of((Class<T>) clazz);
            } catch (ClassNotFoundException e) {
                return Stream.of();
            }
        }

        @Override
        public InvocationPerformer<T> withChildModule(Module childModule) {
            return new InvocationPerformer<>(injector.createChildInjector(childModule), namingScheme);
        }

        @Override
        public InvocationPerformer<T> withNamingSheme(NamingScheme namingSheme) {
            return new InvocationPerformer<>(injector, namingSheme);
        }
    }

    private final Injector injector;

    @Inject
    public NoopGuiceLoader(Injector injector) {
        this.injector = injector;
    }

    @Override
    public <T> T instantiate(ClassName className) throws ClassNotFoundException {
        return new InvocationPerformer<T>(injector, NamingScheme.IDENTITY)
            .instantiate(className);
    }

    @Override
    public <T> InvocationPerformer<T> withNamingSheme(NamingScheme namingSheme) {
        return new InvocationPerformer<>(injector, namingSheme);
    }

    @Override
    public <T> InvocationPerformer<T> withChildModule(Module childModule) {
        return new InvocationPerformer<>(injector.createChildInjector(childModule), NamingScheme.IDENTITY);
    }
}