package com.linagora.tmail.jmap.mail;


import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.method.WithAccountId

case class AiBotSuggestReplyRequest(
                                     accountId: AccountId,
                                     emailId: Id,
                                     userInput: String
                                   ) extends WithAccountId

case class AiBotSuggestReplyResponse(
                                      accountId: AccountId,
                                      suggestion: String
                                    )
//methode compute respse a definir : il faut recupere le contenu de l'email a partie de son id