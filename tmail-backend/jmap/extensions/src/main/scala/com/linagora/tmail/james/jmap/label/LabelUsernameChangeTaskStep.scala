package com.linagora.tmail.james.jmap.label

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.UsernameChangeTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

class LabelUsernameChangeTaskStep @Inject() (labelRepository: LabelRepository) extends UsernameChangeTaskStep {
  override def name(): UsernameChangeTaskStep.StepName = new UsernameChangeTaskStep.StepName("LabelUsernameChangeTaskStep")

  override def priority(): Int = 8

  override def changeUsername(oldUsername: Username, newUsername: Username): Publisher[Void] =
    SFlux(labelRepository.listLabels(oldUsername))
      .flatMap(label => SMono(labelRepository.addLabel(newUsername, label))
        .`then`(SMono(labelRepository.deleteLabel(oldUsername, label.id))))
}
