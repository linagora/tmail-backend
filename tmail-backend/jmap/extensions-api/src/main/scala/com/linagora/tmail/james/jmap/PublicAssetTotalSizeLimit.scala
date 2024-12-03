package com.linagora.tmail.james.jmap

import eu.timepit.refined
import org.apache.james.jmap.core.UnsignedInt.{UnsignedInt, UnsignedIntConstraint}
import org.apache.james.util.Size

import scala.util.{Failure, Success, Try}

object PublicAssetTotalSizeLimit {
  def of(size: Size): Try[PublicAssetTotalSizeLimit] = refined.refineV[UnsignedIntConstraint](size.asBytes()) match {
    case Right(value) => Success(PublicAssetTotalSizeLimit(value))
    case Left(error) => Failure(new NumberFormatException(error))
  }
}

case class PublicAssetTotalSizeLimit(value: UnsignedInt) {
  def asLong(): Long = value.value
}