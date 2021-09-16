package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.longlivedtoken.{DeviceId, LongLivedTokenId, LongLivedTokenSecret}
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.method.WithAccountId

case class TokenCreateRequest(deviceId: DeviceId);

case class LongLivedTokenSetRequest(accountId: AccountId,
                                    create: TokenCreateRequest) extends WithAccountId {
  def validate: Either[IllegalArgumentException, LongLivedTokenSetRequest] =
    create.deviceId.validate
      .map(_ => this)
}

case class TokenCreateResponse(id: LongLivedTokenId,
                               token: LongLivedTokenSecret)

case class LongLivedTokenSetResponse(accountId: AccountId,
                                     created: TokenCreateResponse)

