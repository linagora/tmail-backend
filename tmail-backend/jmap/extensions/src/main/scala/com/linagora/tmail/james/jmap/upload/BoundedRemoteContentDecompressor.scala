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

package com.linagora.tmail.james.jmap.upload

import io.netty.handler.codec.compression.{Brotli, Zstd}
import io.netty.handler.codec.http.HttpContentDecompressor
import reactor.netty.{Connection, NettyPipeline}

object BoundedRemoteContentDecompressor {
  val ACCEPTED_ENCODINGS: String = (Seq("gzip", "deflate") ++
    Option.when(Brotli.isAvailable)("br") ++
    Option.when(Zstd.isAvailable)("zstd")).mkString(", ")

  def install(connection: Connection, maximumSize: Long): Unit = {
    val pipeline = connection.channel().pipeline()
    if (pipeline.get(NettyPipeline.HttpDecompressor) == null) {
      val decompressor = new HttpContentDecompressor(false, maximumAllocation(maximumSize))
      if (pipeline.get(NettyPipeline.ReactiveBridge) == null) {
        pipeline.addLast(NettyPipeline.HttpDecompressor, decompressor)
      } else {
        pipeline.addBefore(NettyPipeline.ReactiveBridge, NettyPipeline.HttpDecompressor, decompressor)
      }
    }
  }

  private[upload] def maximumAllocation(maximumSize: Long): Int =
    Math.min(maximumSize, Int.MaxValue.toLong).toInt
}
