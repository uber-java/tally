// Copyright (c) 2021 Uber Technologies, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.uber.m3.tally;

import org.junit.Test;

import static org.junit.Assert.*;

public class CapableOfTest {
    @Test
    public void capabilities() {
        Capabilities capabilities = new CapableOf(false, true);

        assertFalse(capabilities.reporting());
        assertTrue(capabilities.tagging());

        assertTrue(CapableOf.REPORTING.reporting());
        assertFalse(CapableOf.REPORTING.tagging());

        assertFalse(CapableOf.NONE.reporting());
        assertFalse(CapableOf.NONE.tagging());

        assertTrue(CapableOf.REPORTING_TAGGING.reporting());
        assertTrue(CapableOf.REPORTING_TAGGING.tagging());
    }

    @Test
    public void equalsHashCode() {
        assertNotEquals(null, CapableOf.NONE);
        assertNotEquals(9, CapableOf.NONE);

        assertNotEquals(CapableOf.NONE, CapableOf.REPORTING);
        assertNotEquals(new CapableOf(true, false), new CapableOf(false, true));

        assertEquals(CapableOf.REPORTING, CapableOf.REPORTING);
        assertEquals(CapableOf.REPORTING, new CapableOf(true, false));
        assertEquals(CapableOf.REPORTING.hashCode(), new CapableOf(true, false).hashCode());
        assertEquals(new CapableOf(false, false), new CapableOf(false, false));
    }
}
