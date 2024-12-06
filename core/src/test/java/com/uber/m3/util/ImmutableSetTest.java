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

package com.uber.m3.util;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.*;

public class ImmutableSetTest {
    private HashSet<String> helperSet;
    private ImmutableSet<String> set;

    @Before
    public void setUp() {
        helperSet = new HashSet<>(4, 1);
        helperSet.add("u");
        helperSet.add("b");
        helperSet.add("e");
        helperSet.add("r");

        set = new ImmutableSet<>(helperSet);
    }

    @Test
    public void size() {
        assertEquals(4, set.size());

        set = new ImmutableSet<>(new HashSet<String>(0));

        assertEquals(0, set.size());
    }

    @Test
    public void isEmpty() {
        assertFalse(set.isEmpty());

        set = new ImmutableSet<>(new HashSet<String>(0));

        assertTrue(set.isEmpty());
    }

    @Test
    public void contains() {
        assertTrue(set.contains("u"));
        assertTrue(set.contains("e"));
        assertFalse(set.contains("z"));
    }

    @Test
    public void iterator() {
        helperSet.remove("b");
        helperSet.remove("e");
        helperSet.remove("r");

        set = new ImmutableSet<>(helperSet);

        UnmodifiableIterator<String> setIter = set.iterator();

        assertTrue(setIter.hasNext());
        assertEquals("u", setIter.next());
        assertFalse(setIter.hasNext());
    }

    @Test
    public void toArray() {
        helperSet.remove("b");
        helperSet.remove("e");
        helperSet.remove("r");

        set = new ImmutableSet<>(helperSet);

        assertArrayEquals(new Object[]{"u"}, set.toArray());
        assertArrayEquals(new String[]{"u"}, set.toArray(new String[1]));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void add() {
        set.add("z");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void remove() {
        set.remove("u");
    }

    @Test
    public void containsAll() {
        assertTrue(set.containsAll(helperSet));

        helperSet.add("q");

        assertFalse(set.containsAll(helperSet));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void addAll() {
        set.addAll(new HashSet<String>());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void retainAll() {
        set.retainAll(new HashSet<String>());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void removeAll() {
        set.removeAll(new HashSet<String>());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void clear() {
        set.clear();
    }

    @Test
    public void equals() {
        ImmutableSet<String> equalSet = new ImmutableSet<>(helperSet);

        assertEquals(set, equalSet);
        assertEquals(set.hashCode(), equalSet.hashCode());

        helperSet.add("zz");
        ImmutableSet<String> differentSet = new ImmutableSet<>(helperSet);

        assertNotEquals(set, differentSet);
        assertNotEquals(set.hashCode(), differentSet.hashCode());

        assertNotEquals(null, set);
        assertEquals(set, set);
        assertNotEquals(2, set);
    }

    @Test
    public void builder() {
        ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<String>(3)
            .add("foo")
            .add("bar")
            .add("baz");

        ImmutableSet<String> set = builder.build();

        HashSet<String> testSet = new HashSet<>(2, 1);
        testSet.add("bar");
        testSet.add("baz");

        assertEquals(3, set.size());
        assertTrue(set.contains("foo"));
        assertTrue(set.containsAll(testSet));
        assertFalse(set.contains("nope"));

        testSet.add("another");
        testSet.add("new");
        builder.addAll(testSet);

        set = new ImmutableSet.Builder<String>()
            .add("foo")
            .addAll(testSet)
            .build();
        assertTrue(set.contains("foo"));
        assertTrue(set.containsAll(testSet));
        assertFalse(set.contains("still nope"));
    }
}
