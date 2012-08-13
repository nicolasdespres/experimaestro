/*
 *
 *  This file is part of experimaestro.
 *  Copyright (c) 2011 B. Piwowarski <benjamin@bpiwowar.net>
 *
 *  experimaestro is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  experimaestro is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with experimaestro.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package sf.net.experimaestro.exceptions;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

public class ExperimaestroRuntimeException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	ArrayList<String> context = new ArrayList<String>();

	public ExperimaestroRuntimeException() {
		super();
	}

	public ExperimaestroRuntimeException(String message, Throwable t) {
		super(message, t);
	}

	public ExperimaestroRuntimeException(Throwable t, String format, Object... values) {
		super(String.format(format, values), t);
	}

	public ExperimaestroRuntimeException(String message) {
		super(message);
	}

	public ExperimaestroRuntimeException(String format, Object... values) {
		super(String.format(format, values));
	}

	public ExperimaestroRuntimeException(Throwable t) {
		super(t);
	}

	public void addContext(String string, Object... values) {
		context.add(format(string, values));
	}

	public List<String> getContext() {
		return context;
	}

}