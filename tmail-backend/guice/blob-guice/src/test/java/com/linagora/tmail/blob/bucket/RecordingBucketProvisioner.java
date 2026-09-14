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

package com.linagora.tmail.blob.bucket;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectStoreException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class RecordingBucketProvisioner implements BucketProvisioner {
    private final ConcurrentLinkedQueue<BucketName> provisioned = new ConcurrentLinkedQueue<>();
    private final Set<BucketName> failing;

    public RecordingBucketProvisioner(BucketName... failing) {
        this.failing = ImmutableSet.copyOf(failing);
    }

    @Override
    public Mono<Void> ensureExists(BucketName bucketName) {
        if (failing.contains(bucketName)) {
            return Mono.error(new ObjectStoreException("Bucket '" + bucketName.asString() + "' could not be created"));
        }
        return Mono.fromRunnable(() -> provisioned.add(bucketName));
    }

    public Collection<BucketName> provisioned() {
        return ImmutableList.copyOf(provisioned);
    }
}
