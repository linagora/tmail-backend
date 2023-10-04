package com.linagora.tmail.webadmin.archival;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class InboxArchivalTaskAdditionalInformationDTOTest {
    @Test
    void beanTest() throws Exception {
        JsonSerializationVerifier.dtoModule(InboxArchivalTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(new InboxArchivalTask.AdditionalInformation(Instant.parse("2007-12-03T10:15:30.00Z"), 4, 2))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/inboxArchivalTask.additionalInformation.json"))
            .verify();
    }
}
