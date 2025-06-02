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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/
package com.linagora.tmail;

import static org.apache.james.JamesServerMain.LOGGER;

import org.apache.james.utils.ClassName;
import org.apache.james.utils.ExtensionConfiguration;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;


public class ExtensionModuleProvider {

    public static com.google.inject.Module extentionModules(ExtensionConfiguration configuration) {
        Injector injector = Guice.createInjector();
        com.google.inject.Module additionalExtensionBindings = Modules.combine(configuration.getAdditionalGuiceModulesForExtensions()
            .stream()
            .map(Throwing.<ClassName, Module>function(className -> instanciate(injector, className)))
            .peek(module -> LOGGER.info("Enabling injects contained in " + module.getClass().getCanonicalName()))
            .collect(ImmutableList.toImmutableList()));
        return additionalExtensionBindings;
    }

    private static <T> T instanciate(Injector injector, ClassName className) throws ClassNotFoundException {
        try {
            Class<?> clazz = ExtensionModuleProvider.class.getClassLoader().loadClass(className.getName());
            return (T) injector.getInstance(clazz);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Class not found: {}", className.getName(), e);
            throw e;
        }
    }
}
