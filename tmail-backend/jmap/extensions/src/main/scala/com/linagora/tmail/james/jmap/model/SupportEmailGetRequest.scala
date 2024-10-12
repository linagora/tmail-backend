package com.linagora.tmail.james.jmap.model

import org.apache.james.jmap.method.WithoutAccountId

case class SupportEmailGetRequest() extends WithoutAccountId {}

case class SupportEmailGetResponse(mailAddress: String) {}
