package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import com.linagora.tmail.james.jmap.publicAsset.{PublicAssetId, PublicURI}
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class PublicAssetGetRequest(accountId: AccountId,
                                 ids: Option[Set[Id]] = None) extends WithAccountId

case class PublicAssetGetResponse(accountId: AccountId,
                                  state: UuidState,
                                  list: Seq[PublicAssetDTO],
                                  notFound: Seq[String])

case class PublicAssetDTO(id: PublicAssetId,
                          publicURI: PublicURI,
                          size: Size,
                          contentType: ImageContentType,
                          identityIds: Seq[IdentityId] = Seq.empty)