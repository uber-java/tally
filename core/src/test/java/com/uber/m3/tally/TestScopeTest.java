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
    public void getInstance() {
        TestScope testScope = TestScope.create();
        assertNotNull(testScope);
        assertThat(testScope, instanceOf(Scope.class));
        assertThat(testScope, instanceOf(ScopeImpl.class));

        assertNotNull(testScope.capabilities());
        assertFalse(testScope.capabilities().reporting());
        assertFalse(testScope.capabilities().tagging());
    }

    @Test
    public void prefixTags() {
        Map<String, String> tags = ImmutableMap.of("key", "value");
        TestScope testScope = TestScope.create("prefix", tags);
        testScope.counter("counter").inc(1);

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<String, CounterSnapshot> counters = snapshot.counters();
        assertNotNull(counters);
        assertEquals(1, counters.size());
        assertNotNull(counters.get("prefix.counter+key=value"));
        assertEquals("prefix.counter", counters.get("prefix.counter+key=value").name());
        assertEquals(tags, counters.get("prefix.counter+key=value").tags());
        assertEquals(1, counters.get("prefix.counter+key=value").value());
    }
}
