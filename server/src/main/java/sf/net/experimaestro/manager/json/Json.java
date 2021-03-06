package sf.net.experimaestro.manager.json;

/*
 * This file is part of experimaestro.
 * Copyright (c) 2014 B. Piwowarski <benjamin@bpiwowar.net>
 *
 * experimaestro is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * experimaestro is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with experimaestro.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.google.gson.stream.JsonWriter;
import sf.net.experimaestro.manager.QName;

import java.io.IOException;
import java.io.Writer;

/**
 * Base class for all JSON objects
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 */
public interface Json {
    Json clone();

    /**
     * Returns true if this Json object is a simple type
     */
    default boolean isSimple() {
        return true;
    }

    /**
     * Returns the simple value underlying this object
     *
     * @return
     */
    Object get();

    /**
     * Get the XPM type
     */
    QName type();

    default boolean canIgnore(JsonWriterOptions options) {
        return false;
    }

    /**
     * Write a normalized version of the JSON
     */
    default void writeDescriptorString(Writer writer, JsonWriterOptions options) throws IOException {
        write(writer);
    }

    /**
     * Write a normalized version of the JSON
     */
    default void writeDescriptorString(Writer writer) throws IOException {
        writeDescriptorString(writer, JsonWriterOptions.DEFAULT_OPTIONS);
    }

    /**
     * Write a JSON representation
     *
     * @param out
     * @throws IOException
     */
    void write(Writer out) throws IOException;


    /**
     * Write with a structured writer
     * @param out The writer
     */
    void write(JsonWriter out) throws IOException;
}
