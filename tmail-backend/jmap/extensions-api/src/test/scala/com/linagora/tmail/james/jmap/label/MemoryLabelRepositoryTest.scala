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

package com.linagora.tmail.james.jmap.label

import org.apache.james.events.delivery.InVmEventDelivery
import org.apache.james.events.{EventBus, InVMEventBus, MemoryEventDeadLetters, RetryBackoffConfiguration}
import org.apache.james.metrics.tests.RecordingMetricFactory

class MemoryLabelRepositoryTest extends LabelRepositoryContract {
  val inVMEventBus: InVMEventBus = new InVMEventBus(
    new InVmEventDelivery(new RecordingMetricFactory()),
    RetryBackoffConfiguration.FAST,
    new MemoryEventDeadLetters())
  val memoryLabelRepository: MemoryLabelRepository = new MemoryLabelRepository(inVMEventBus)

  override def testee: LabelRepository = memoryLabelRepository
  override def eventBus: EventBus = inVMEventBus
}
