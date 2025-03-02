package com.linagora.tmail.james;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.linagora.tmail.DockerOpenPaasSetupSingleton;
import com.linagora.tmail.OpenPaasUser;
import com.linagora.tmail.james.common.InvitationEmailData;
import com.linagora.tmail.james.common.User;

public class OpenPaasEventInvitationParameterResolver implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == InvitationEmailData.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) throws ParameterResolutionException {
        OpenPaasUser sender = newOpenPaasUser();
        OpenPaasUser receiver = newOpenPaasUser();

        return new InvitationEmailData(asUser(sender), asUser(receiver));
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
