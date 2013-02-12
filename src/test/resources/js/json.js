/*
 * This file is part of experimaestro.
 * Copyright (c) 2012 B. Piwowarski <benjamin@bpiwowar.net>
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

// Tests for JSON way of transmitting information
// (1) for inputs
// (2) for outpus

// START SNIPPET: task

// Namespace
var tests = new Namespace("xpm.tests");



// Add the task to the list of available factories
tasks.tests::task = {
    inputs: {
        x: { value: "xs:integer", default: 3 }      
        // could be xml: "...", alternative: ...,   
    },
    run: function(p) {
        // Without unwrap, it would create an XML
        // like <tests:x><x>VALUE</x></tests:x>
        return {  "tests:x": p.x.text() }
    }
};

// END SNIPPET: task
function test_json_plan() {
    var r = tasks.tests::task.run({x: 10});
    r = r[0];
	if (r == undefined || r != 10)
		throw new java.lang.String.format("Value [%s] is different from 10", r);
}



