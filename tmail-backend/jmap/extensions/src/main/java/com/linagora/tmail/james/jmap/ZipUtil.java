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

package com.linagora.tmail.james.jmap;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public class ZipUtil {
    public record ZipEntryStreamSource(InputStream inputStream, String entryName) {}

    public record ZipEntryData(String fileName, String content) {}

    public static class ReactorNettyOutputStream extends OutputStream {
        private final FluxSink<byte[]> sink;

        public ReactorNettyOutputStream(FluxSink<byte[]> sink) {
            this.sink = sink;
        }

        @Override
        public void write(int b) {
            sink.next(new byte[]{(byte) b});
        }

        @Override
        public void write(byte[] b, int off, int len) {
            sink.next(java.util.Arrays.copyOfRange(b, off, off + len));
        }
    }

    public static Flux<byte[]> createZipStream(Collection<ZipEntryStreamSource> streamSources) {
        return Flux.create(sink -> {
            Collection<ZipEntryStreamSource> updatedStreamSources = updateEntryName(streamSources);      // to handle the case of duplicated entry names,
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(new ReactorNettyOutputStream(sink))) {
                for (ZipEntryStreamSource streamSource: updatedStreamSources) {
                    zipOutputStream.putNextEntry(new ZipEntry(streamSource.entryName()));
                    try (InputStream source = streamSource.inputStream()) {
                        source.transferTo(zipOutputStream);
                        zipOutputStream.closeEntry();
                    }
                }
                zipOutputStream.finish();
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    public static List<ZipEntryData> readZipData(InputStream inputStream) {
        ImmutableList.Builder<ZipEntryData> listBuilder = ImmutableList.builder();

        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String fileContent = IOUtils.toString(zipInputStream, StandardCharsets.UTF_8);
                    listBuilder.add(new ZipEntryData(entry.getName(), fileContent));
                }
                zipInputStream.closeEntry();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return listBuilder.build();
    }

    @VisibleForTesting
    static Collection<ZipEntryStreamSource> updateEntryName(Collection<ZipEntryStreamSource> streamSources) {
        Map<String, Integer> nameCount = new HashMap<>();
        ImmutableList.Builder<ZipEntryStreamSource> result = new ImmutableList.Builder<>();

        for (ZipEntryStreamSource streamSource : streamSources) {
            String entryName = streamSource.entryName();

            if (!nameCount.containsKey(entryName)) {
                nameCount.put(entryName, 0);
                result.add(new ZipEntryStreamSource(streamSource.inputStream(), entryName));
            } else {
                String baseName, extension = "";
                int dotIndex = entryName.lastIndexOf('.');
                if (dotIndex != -1) {
                    baseName = entryName.substring(0, dotIndex);
                    extension = entryName.substring(dotIndex);
                } else {
                    baseName = entryName;
                }

                int count = nameCount.get(entryName) + 1;
                String newEntryName;
                do {
                    newEntryName = baseName + "_" + count + extension;
                    count++;
                } while (nameCount.containsKey(newEntryName));

                nameCount.put(entryName, count - 1);
                nameCount.put(newEntryName, 0);
                result.add(new ZipEntryStreamSource(streamSource.inputStream(), newEntryName));
            }
        }
        return result.build();
    }
}
