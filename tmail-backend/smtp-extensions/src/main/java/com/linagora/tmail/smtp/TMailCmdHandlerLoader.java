package com.linagora.tmail.smtp;

import java.util.List;
import java.util.function.Function;

import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.smtpserver.CoreCmdHandlerLoader;
import org.apache.james.smtpserver.UsersRepositoryAuthHook;

public class TMailCmdHandlerLoader implements HandlersPackage {

    private static final Function<String, String> CMD_HANDLER_MAPPER = handler ->  {
        if (handler.equals(UsersRepositoryAuthHook.class.getName())) {
            return TMailUsersRepositoryAuthHook.class.getName();
        }
        return handler;
    };

    private final List<String> commands;

    public TMailCmdHandlerLoader() {
        this.commands = new CoreCmdHandlerLoader().getHandlers().stream()
            .map(CMD_HANDLER_MAPPER)
            .toList();
    }

    @Override
    public List<String> getHandlers() {
        return commands;
    }
}
