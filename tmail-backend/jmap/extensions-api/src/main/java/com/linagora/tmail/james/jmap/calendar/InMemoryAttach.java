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

package com.linagora.tmail.james.jmap.calendar;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.codec.DecoderException;

import com.google.common.collect.ImmutableList;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarParserFactory;
import net.fortuna.ical4j.data.ContentHandlerContext;
import net.fortuna.ical4j.data.DefaultPropertyFactorySupplier;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.PropertyFactory;
import net.fortuna.ical4j.model.parameter.Encoding;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.util.DecoderFactory;

/**
 * Builds ATTACH properties whose inline (ENCODING) value is kept on the heap.
 *
 * <p>ical4j 4.4.0 decodes it into a new temporary file it maps in memory, and only deletes on JVM exit: every parse or
 * factory based copy of an ATTACH would leave a file in {@code java.io.tmpdir}, and an entry in the JVM wide set of files
 * to delete on exit.
 */
public final class InMemoryAttach {
    public static class Factory extends Attach.Factory {
        private static final long serialVersionUID = 1L;

        @Override
        public Attach createProperty(ParameterList parameters, String value) {
            return InMemoryAttach.create(parameters, value);
        }
    }

    // The ical4j factories hold no state: they are shared by all the parsed properties
    private static final List<PropertyFactory<?>> PROPERTY_FACTORIES = Stream.concat(
            Stream.<PropertyFactory<?>>of(new Factory()),
            new DefaultPropertyFactorySupplier().get().stream())
        .collect(ImmutableList.toImmutableList());

    /**
     * The ical4j property factories, ATTACH being built by {@link Factory}.
     */
    public static List<PropertyFactory<?>> propertyFactories() {
        return PROPERTY_FACTORIES;
    }

    /**
     * A calendar builder parsing ATTACH with {@link Factory}, and timezones with {@link ICal4JTimeZoneRegistry}, which
     * bounds the cost of their rules.
     */
    public static CalendarBuilder calendarBuilder(ContentHandlerContext context) {
        ICal4JTimeZoneRegistry timeZoneRegistry = new ICal4JTimeZoneRegistry();
        return new CalendarBuilder(CalendarParserFactory.getInstance().get(),
            context.withPropertyFactorySupplier(InMemoryAttach::propertyFactories)
                .withComponentFactorySupplier(timeZoneRegistry::componentFactories),
            timeZoneRegistry);
    }

    /**
     * An ATTACH holding a URI, or a value ical4j cannot decode, is built by ical4j, which creates no file for them.
     */
    public static Attach create(ParameterList parameters, String value) {
        Optional<Encoding> encoding = parameters.getParameter(Parameter.ENCODING);
        if (encoding.isEmpty()) {
            return new Attach(parameters, value);
        }
        try {
            byte[] decoded = DecoderFactory.getInstance().createBinaryDecoder(encoding.get())
                .decode(value.getBytes(StandardCharsets.US_ASCII));
            return new Attach(parameters, ByteBuffer.wrap(decoded));
        } catch (UnsupportedEncodingException | DecoderException e) {
            return new Attach(parameters, value);
        }
    }

    /**
     * A copy holding its own bytes. ical4j's {@link Attach#copy()} goes through the ical4j factory, hence a file.
     */
    public static Attach copy(Attach attach) {
        byte[] binary = attach.getBinaryData();
        if (binary == null) {
            return (Attach) attach.copy();
        }
        return new Attach(new ParameterList(attach.getParameterList().getAll()), ByteBuffer.wrap(binary.clone()));
    }

    private InMemoryAttach() {
    }
}
