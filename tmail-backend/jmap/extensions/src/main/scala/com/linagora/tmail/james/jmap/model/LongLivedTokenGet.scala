package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.longlivedtoken.{LongLivedTokenFootPrint, LongLivedTokenId}
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class LongLivedTokenGetRequest(accountId: AccountId,
                              ids: Option[Set[LongLivedTokenId]]) extends WithAccountId {
  def notFound(actualIds: Set[LongLivedTokenId]): Set[LongLivedTokenId] = ids
    .map(requestedIds => requestedIds.diff(actualIds))
    .getOrElse(Set[LongLivedTokenId]())
}

case class LongLivedTokenGetResponse(accountId: AccountId,
                               state: UuidState,
                               list: List[LongLivedTokenFootPrint],
                               notFound: Set[LongLivedTokenId])