/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.common.probe

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.publicAsset.{PublicAssetCreationRequest, PublicAssetId, PublicAssetRepository, PublicAssetStorage}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.utils.GuiceProbe
import reactor.core.scala.publisher.{SFlux, SMono}

class PublicAssetProbeModule extends AbstractModule {
  override def configure(): Unit =
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[PublicAssetProbe])
}

class PublicAssetProbe @Inject()(publicAssetRepository: PublicAssetRepository) extends GuiceProbe {
  def create(username: Username, creationRequest: PublicAssetCreationRequest): PublicAssetStorage =
    SMono(publicAssetRepository.create(username, creationRequest)).block()

  def list(username: Username): Seq[PublicAssetStorage] =
    SFlux(publicAssetRepository.list(username)).collectSeq().block()

  def getByUsernameAndAssetId(username: Username, assetId: PublicAssetId): PublicAssetStorage =
    SMono(publicAssetRepository.get(username, assetId)).block()
}
