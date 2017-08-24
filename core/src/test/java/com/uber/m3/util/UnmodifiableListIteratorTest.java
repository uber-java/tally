// Copyright (c) 2017 Uber Technologies, Inc.
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

package com.uber.m3.util;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnmodifiableListIteratorTest {
    private UnmodifiableListIterator<String> iter;

    @Before
    public void setUp() {
        ArrayList<String> helperList = new ArrayList<>(2);
        helperList.add("ub");
        helperList.add("er");

        ImmutableList<String> list = new ImmutableList<>(helperList);

        iter = list.listIterator();
    }

    @Test
    public void hasNext() {
        assertTrue(iter.hasNext());

        while (iter.hasNext()) {
            iter.next();
        }

        assertFalse(iter.hasNext());
    }

    @Test
    public void next() {
        assertEquals("ub", iter.next());
        assertEquals("er", iter.next());
    }

    @Test(expected = NoSuchElementException.class)
    public void nextException() {
        while (iter.hasNext()) {
            iter.next();
        }

        iter.next();
    }

    @Test
    public void hasPrevious() {
        assertFalse(iter.hasPrevious());

        iter.next();

        assertTrue(iter.hasPrevious());
    }

    @Test
    public void previous() {
        while (iter.hasNext()) {
            iter.next();
        }

        assertEquals("er", iter.previous());
        assertEquals("ub", iter.previous());
    }

    @Test(expected = NoSuchElementException.class)
    public void previousException() {
        iter.previous();
    }

    @Test
    public void nextIndex() {
        assertEquals(0, iter.nextIndex());
        assertEquals(0, iter.nextIndex());

        iter.next();

        assertEquals(1, iter.nextIndex());

        iter.next();

        assertEquals(2, iter.nextIndex());

        iter.previous();

        assertEquals(1, iter.nextIndex());
    }

    @Test
    public void previousIndex() {
        assertEquals(-1, iter.previousIndex());

        iter.next();
        iter.next();

        assertEquals(1, iter.previousIndex());

        iter.previous();

        assertEquals(0, iter.previousIndex());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void remove() {
        iter.remove();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void set() {
        iter.set("zz");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void add() {
        iter.add("zz");
    }
}
