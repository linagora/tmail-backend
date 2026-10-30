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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public class ZipUtil {
    private static final String DEFAULT_ENTRY_NAME = "noname";
    private static final Splitter PATH_SEPARATORS = Splitter.on(CharMatcher.anyOf("/\\"));
    private static final CharMatcher CONTROL_CHARACTERS = CharMatcher.javaIsoControl();
    private static final Pattern DRIVE_PREFIX = Pattern.compile("^([a-zA-Z]:\\s*)+");
    // Leaves room, under the usual 255 bytes file system limit, for the "_<n>" suffix added by updateEntryName
    private static final int MAX_ENTRY_NAME_BYTES = 200;
    private static final int MAX_EXTENSION_BYTES = 32;

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
    static String sanitizeEntryName(String entryName) {
        String lastPathComponent = Iterables.getLast(PATH_SEPARATORS.split(Strings.nullToEmpty(entryName)));
        String withoutControlCharacters = CONTROL_CHARACTERS.removeFrom(lastPathComponent).trim();
        String sanitized = DRIVE_PREFIX.matcher(withoutControlCharacters)
            .replaceFirst("")
            .trim();
        if (sanitized.isEmpty() || sanitized.equals(".") || sanitized.equals("..")) {
            return DEFAULT_ENTRY_NAME;
        }
        return truncateEntryName(sanitized);
    }

    private static String truncateEntryName(String entryName) {
        if (utf8Length(entryName) <= MAX_ENTRY_NAME_BYTES) {
            return entryName;
        }
        String extension = boundedExtension(entryName);
        String baseName = entryName.substring(0, entryName.length() - extension.length());
        return truncateToUtf8Bytes(baseName, MAX_ENTRY_NAME_BYTES - utf8Length(extension)) + extension;
    }

    private static String boundedExtension(String entryName) {
        int dotIndex = entryName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return "";
        }
        String extension = entryName.substring(dotIndex);
        if (utf8Length(extension) > MAX_EXTENSION_BYTES) {
            return "";
        }
        return extension;
    }

    private static String truncateToUtf8Bytes(String value, int maxBytes) {
        AtomicInteger usedBytes = new AtomicInteger();
        return value.codePoints()
            .takeWhile(codePoint -> usedBytes.addAndGet(utf8Length(Character.toString(codePoint))) <= maxBytes)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    @VisibleForTesting
    static Collection<ZipEntryStreamSource> updateEntryName(Collection<ZipEntryStreamSource> streamSources) {
        Map<String, Integer> nameCount = new HashMap<>();
        ImmutableList.Builder<ZipEntryStreamSource> result = new ImmutableList.Builder<>();

        for (ZipEntryStreamSource streamSource : streamSources) {
            String entryName = sanitizeEntryName(streamSource.entryName());

            if (!nameCount.containsKey(entryName)) {
                nameCount.put(entryName, 0);
                result.add(new ZipEntryStreamSource(streamSource.inputStream(), entryName));
            } else {
                String baseName = "";
                String extension = "";
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
