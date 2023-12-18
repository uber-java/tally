// Copyright (c) 2023 Uber Technologies, Inc.
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

import com.uber.m3.util.ImmutableMap;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class TestScopeTest {

    @Test
    public void testCreate() {
        TestScope testScope = TestScope.create();
        assertNotNull(testScope);
        assertThat(testScope, instanceOf(Scope.class));

        assertNotNull(testScope.capabilities());
        assertFalse(testScope.capabilities().reporting());
        assertFalse(testScope.capabilities().tagging());

        ImmutableMap<String, String> tags = ImmutableMap.of("key", "value");

        testScope.tagged(tags).counter("counter").inc(1);

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, CounterSnapshot> counters = snapshot.counters();
        assertNotNull(counters);
        assertEquals(1, counters.size());

        CounterSnapshot counterSnapshot = counters.get(new ScopeKey("counter", tags));
        assertNotNull(counterSnapshot);

        assertEquals("counter", counterSnapshot.name());
        assertEquals(tags, counterSnapshot.tags());
        assertEquals(1, counterSnapshot.value());
    }

    @Test
    public void createWithPrefixAndTags() {
        Map<String, String> tags = ImmutableMap.of("key", "value");
        TestScope testScope = TestScope.create("prefix", tags);
        testScope.tagged(ImmutableMap.of("other_key", "other_value")).counter("counter").inc(1);

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, CounterSnapshot> counters = snapshot.counters();
        assertNotNull(counters);
        assertEquals(1, counters.size());

        ImmutableMap<String, String> totalTags = ImmutableMap.of("key", "value", "other_key", "other_value");
        CounterSnapshot counterSnapshot = counters.get(new ScopeKey("prefix.counter", totalTags));

        assertNotNull(counterSnapshot);
        assertEquals("prefix.counter", counterSnapshot.name());
        assertEquals(totalTags, counterSnapshot.tags());
        assertEquals(1, counterSnapshot.value());
    }

    @Test
    public void testCreateWithTagsAndSubscope() {
        ImmutableMap<String, String> tags = ImmutableMap.of("key", "value");
        TestScope testScope = TestScope.create("", tags);

        ImmutableMap<String, String> subScopeTags = ImmutableMap.of("key", "other_value");
        testScope.tagged(subScopeTags).subScope("subscope").counter("counter").inc(1);

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, CounterSnapshot> counters = snapshot.counters();
        assertNotNull(counters);
        assertEquals(1, counters.size());

        CounterSnapshot counterSnapshot = counters.get(new ScopeKey("subscope.counter", subScopeTags));
        assertNotNull(counterSnapshot);

        assertEquals("subscope.counter", counterSnapshot.name());
        assertEquals(subScopeTags, counterSnapshot.tags());
        assertEquals(1, counterSnapshot.value());
    }
}

