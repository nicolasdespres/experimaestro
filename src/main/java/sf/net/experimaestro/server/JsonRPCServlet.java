/*
 * This file is part of experimaestro.
 * Copyright (c) 2013 B. Piwowarski <benjamin@bpiwowar.net>
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

package sf.net.experimaestro.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import sf.net.experimaestro.manager.Repositories;
import sf.net.experimaestro.scheduler.Scheduler;
import sf.net.experimaestro.utils.log.Logger;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Stack;

/**
 * Json-RPC2 servlet
 *
 * @author B. Piwowarski <benjamin@bpiwowar.net>
 * @date 28/3/13
 */
public class JsonRPCServlet extends HttpServlet {
    final static private Logger LOGGER = Logger.getLogger();

    private final Scheduler scheduler;
    private final Repositories repository;

    public JsonRPCServlet(Scheduler scheduler, Repositories repository) {

        this.scheduler = scheduler;
        this.repository = repository;
    }


    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        ServletInputStream inputStream = req.getInputStream();
        InputStreamReader reader = new InputStreamReader(inputStream);

        JSONParser parser = new JSONParser();
        JsonStreamHandler handler = new JsonStreamHandler(req, resp);

        try {
            parser.parse(reader, handler, true);
        } catch (ParseException e) {
            LOGGER.error(e);
        }

        ServletOutputStream outputStream = resp.getOutputStream();
        outputStream.flush();
        outputStream.close();

    }

    private class JsonStreamHandler implements ContentHandler {
        private final JsonRPCMethods jsonRPCMethods;
        Stack<Object> stack = new Stack<>();

        private JsonStreamHandler(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
            ServletOutputStream outputStream = resp.getOutputStream();
            final PrintWriter pw = new PrintWriter(outputStream);

            resp.setContentType("text/json");
            resp.setStatus(HttpServletResponse.SC_OK);

            jsonRPCMethods = new JsonRPCMethods(scheduler, repository, new JSONRPCRequest() {
                @Override
                public void sendJSONString(String message) throws IOException {
                    pw.print(message);
                    pw.flush();
                }
            });
        }

        @Override
        public void startJSON() throws ParseException, IOException {
            assert stack.isEmpty();
        }

        @Override
        public void endJSON() throws ParseException, IOException {
            assert stack.size() == 1;
            jsonRPCMethods.handleJSON((JSONObject)stack.pop());
        }

        private void checkArray() {
            if (stack.size() < 2)
                return;

            Object container = stack.get(stack.size() - 2);
            if (container instanceof JSONArray) {
                ((JSONArray) container).add(stack.pop());
            }
        }


        @Override
        public boolean startObject() throws ParseException, IOException {
            stack.push(new JSONObject());
            return true;
        }

        @Override
        public boolean endObject() throws ParseException, IOException {
            checkArray();
            return true;
        }

        @Override
        public boolean startObjectEntry(String key) throws ParseException, IOException {
            stack.push(key);
            return true;
        }

        @Override
        public boolean endObjectEntry() throws ParseException, IOException {
            Object value = stack.pop();
            String key = (String) stack.pop();
            JSONObject object = (JSONObject) stack.peek();
            object.put(key, value);
            return true;
        }

        @Override
        public boolean startArray() throws ParseException, IOException {
            stack.push(new JSONArray());
            return true;
        }

        @Override
        public boolean endArray() throws ParseException, IOException {
            checkArray();
            return true;
        }

        @Override
        public boolean primitive(Object value) throws ParseException, IOException {
            stack.push(value);
            checkArray();
            return true;
        }
    }
}