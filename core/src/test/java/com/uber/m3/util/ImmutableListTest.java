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

import java.util.ArrayList;

import static org.junit.Assert.*;

public class ImmutableListTest {
    private ArrayList<String> helperList;
    private ImmutableList<String> list;

    @Before
    public void setUp() {
        helperList = new ArrayList<>(2);
        helperList.add("ub");
        helperList.add("er");

        list = new ImmutableList<>(helperList);
    }

    @Test
    public void size() {
        assertEquals(2, list.size());

        list = new ImmutableList<>(new ArrayList<String>(0));

        assertEquals(0, list.size());
    }

    @Test
    public void isEmpty() {
        assertFalse(list.isEmpty());

        list = new ImmutableList<>(new ArrayList<String>(0));

        assertTrue(list.isEmpty());
    }

    @Test
    public void contains() {
        assertTrue(list.contains("ub"));
        assertTrue(list.contains("er"));
        assertFalse(list.contains("u"));
    }

    @Test
    public void iterator() {
        UnmodifiableIterator<String> listIter = list.iterator();

        assertTrue(listIter.hasNext());
        assertEquals("ub", listIter.next());
        assertEquals("er", listIter.next());
        assertFalse(listIter.hasNext());
    }

    @Test
    public void toArray() {
        assertArrayEquals(new Object[]{"ub", "er"}, list.toArray());
        assertArrayEquals(new String[]{"ub", "er"}, list.toArray(new String[2]));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void add() {
        list.add("zz");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void addIndex() {
        list.add(1, "zz");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void remove() {
        list.remove("ub");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void removeIndex() {
        list.remove(4);
    }

    @Test
    public void containsAll() {
        assertTrue(list.containsAll(helperList));

        helperList.add("zz");

        assertFalse(list.containsAll(helperList));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void addAll() {
        list.addAll(helperList);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void addAllIndex() {
        list.addAll(1, helperList);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void removeAll() {
        list.removeAll(helperList);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void retainAll() {
        list.retainAll(helperList);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void clear() {
        list.clear();
    }

    @Test
    public void get() {
        assertEquals("ub", list.get(0));
        assertEquals("er", list.get(1));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void set() {
        list.set(1, "zz");
    }

    @Test
    public void indexOf() {
        assertEquals(0, list.indexOf("ub"));
        assertEquals(1, list.indexOf("er"));
        assertEquals(-1, list.indexOf("u"));
    }

    @Test
    public void lastIndexOf() {
        assertEquals(0, list.lastIndexOf("ub"));

        helperList.add("ub");
        list = new ImmutableList<>(helperList);

        assertEquals(2, list.lastIndexOf("ub"));
    }

    @Test
    public void listIterator() {
        UnmodifiableListIterator<String> listIter = list.listIterator();

        assertTrue(listIter.hasNext());
        assertEquals("ub", listIter.next());
        assertEquals(1, listIter.nextIndex());
        assertEquals("er", listIter.next());
        assertFalse(listIter.hasNext());

        listIter = list.listIterator(1);

        assertTrue(listIter.hasNext());
        assertEquals(1, listIter.nextIndex());
        assertEquals("er", listIter.next());
        assertFalse(listIter.hasNext());
    }

    @Test
    public void subList() {
        ImmutableList<String> subList = list.subList(0, 1);

        helperList.remove(1);
        ImmutableList<String> expectedList = new ImmutableList<>(helperList);

        assertEquals(expectedList, subList);
    }

    @Test
    public void equals() {
        ImmutableList<String> sameList = new ImmutableList<>(helperList);

        helperList.add("zz");
        ImmutableList<String> differentList = new ImmutableList<>(helperList);

        assertEquals(list, sameList);
        assertEquals(list.hashCode(), sameList.hashCode());
        assertNotEquals(list, differentList);
        assertNotEquals(list.hashCode(), differentList.hashCode());

        assertNotEquals(null, list);
        assertEquals(list, list);
        assertNotEquals(1, list);
    }
}
