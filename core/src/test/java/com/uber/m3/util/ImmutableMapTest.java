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

import java.util.HashMap;

import static org.junit.Assert.*;

public class ImmutableMapTest {
    private HashMap<String, String> helperMap;
    private ImmutableMap<String, String> map;

    @Before
    public void setUp() {
        helperMap = new HashMap<>(3, 1);
        helperMap.put("key1", "val1");
        helperMap.put("key2", "val2");
        helperMap.put("key3", "val3");

        map = new ImmutableMap<>(helperMap);
    }

    @Test
    public void of() {
        ImmutableMap<String, String> map = ImmutableMap.of("k1", "v1");
        assertEquals(1, map.size());
        assertEquals("v1", map.get("k1"));

        map = ImmutableMap.of("k1", "v1", "k2", "v2");
        assertEquals(2, map.size());
        assertEquals("v1", map.get("k1"));
        assertEquals("v2", map.get("k2"));

        map = ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3");
        assertEquals(3, map.size());
        assertEquals("v1", map.get("k1"));
        assertEquals("v2", map.get("k2"));
        assertEquals("v3", map.get("k3"));
    }

    @Test
    public void size() {
        assertEquals(3, map.size());

        map = new ImmutableMap<>(new HashMap<String, String>(0, 1));

        assertEquals(0, map.size());
    }

    @Test
    public void isEmpty() {
        assertFalse(map.isEmpty());

        map = new ImmutableMap<>(new HashMap<String, String>(0, 1));

        assertTrue(map.isEmpty());
    }

    @Test
    public void containsKey() {
        assertTrue(map.containsKey("key1"));
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsKey("key3"));

        assertFalse(map.containsKey("key5"));
    }

    @Test
    public void containsValue() {
        assertTrue(map.containsValue("val1"));
        assertTrue(map.containsValue("val2"));
        assertTrue(map.containsValue("val3"));

        assertFalse(map.containsValue("key1"));
    }

    @Test
    public void get() {
        assertEquals("val1", map.get("key1"));

        assertNull(map.get("key9"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void put() {
        map.put("key", "val");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void remove() {
        map.remove("key");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void putAll() {
        map.putAll(new HashMap<String, String>());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void clear() {
        map.clear();
    }

    @Test
    public void keySet() {
        assertEquals(new ImmutableMap<>(helperMap).keySet(), map.keySet());
    }

    @Test
    public void values() {
        assertEquals(new ImmutableMap<>(helperMap).values(), map.values());
    }

    @Test
    public void entrySet() {
        assertEquals(new ImmutableMap<>(helperMap).entrySet(), map.entrySet());
    }

    @Test
    public void builderPutAll() {
        ImmutableMap<String, String> sameMap = new ImmutableMap.Builder<String, String>()
            .putAll(helperMap).build();

        assertEquals(map, sameMap);
    }

    @Test
    public void equals() {
        assertNotEquals(null, map);
        assertNotEquals(1, map);
        assertEquals(map, map);

        ImmutableMap<String, String> sameMap = new ImmutableMap<>(helperMap);

        assertEquals(map, sameMap);
        assertEquals(map.hashCode(), sameMap.hashCode());

        helperMap.put("key7", "val7");
        ImmutableMap<String, String> differentMap = new ImmutableMap<>(helperMap);

        assertNotEquals(map, differentMap);
        assertNotEquals(map.hashCode(), differentMap.hashCode());

        assertEquals(ImmutableMap.EMPTY, new ImmutableMap(new HashMap()));
    }

    @Test
    public void toStringTest() {
        assertEquals(helperMap.toString(), map.toString());

        assertEquals(new HashMap<>(0).toString(), ImmutableMap.EMPTY.toString());
    }
}
