package com.linagora.tmail.integration.postgres;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.integration.RecomputeQuotaTeamMailboxesRouteIntegrationContract;

public class PostgresRecomputeQuotaTeamMailboxesRouteIntegrationTest extends RecomputeQuotaTeamMailboxesRouteIntegrationContract {

    @RegisterExtension
    static JamesServerExtension testExtension = PostgresWebAdminBase.JAMES_SERVER_EXTENSION_SUPPLIER.get().build();

}
