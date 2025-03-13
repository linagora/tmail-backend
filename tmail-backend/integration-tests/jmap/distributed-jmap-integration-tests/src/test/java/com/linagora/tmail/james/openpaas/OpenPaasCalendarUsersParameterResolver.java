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

package com.linagora.tmail.james.openpaas;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.linagora.tmail.DockerOpenPaasSetupSingleton;
import com.linagora.tmail.OpenPaasUser;
import com.linagora.tmail.james.common.CalendarUsers;
import com.linagora.tmail.james.common.User;

public class OpenPaasCalendarUsersParameterResolver implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == CalendarUsers.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) throws ParameterResolutionException {
        OpenPaasUser userOne = newOpenPaasUser();
        OpenPaasUser userTwo = newOpenPaasUser();
        OpenPaasUser userThree = newOpenPaasUser();
        OpenPaasUser userFour = newOpenPaasUser();

        return new CalendarUsers(asUser(userOne), asUser(userTwo), asUser(userThree), asUser(userFour));
    }

    private OpenPaasUser newOpenPaasUser() {
        return DockerOpenPaasSetupSingleton.singleton
            .getOpenPaaSProvisioningService()
            .createUser()
            .block();
    }

    private User asUser(OpenPaasUser openPaasUser) {
        return new User(openPaasUser.firstname() + " " + openPaasUser.lastname(),
            openPaasUser.email(), openPaasUser.password());
    }
}
