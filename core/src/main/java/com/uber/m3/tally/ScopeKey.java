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

import java.util.Objects;

/**
 * ScopeKey encapsulates the data to uniquely identify the {@link Scope}.
 * This object overrides {@link #equals(Object)} and {@link #hashCode()} methods, so it can be used in Hash based {@link java.util.Map} implementations, to retrieve the corresponding {@link Scope}.
 */
public final class ScopeKey {
    private final String prefix;
    private final ImmutableMap<String, String> tags;

    public ScopeKey(String prefix, ImmutableMap<String, String> tags) {
        this.prefix = (prefix == null) ? "" : prefix;
        this.tags = (tags == null) ? ImmutableMap.EMPTY : tags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, tags);
    }

    @Override
    public boolean equals(Object otherObj) {
        if (this == otherObj) {
            return true;
        }
        if (otherObj == null) {
            return false;
        }
        if (getClass() != otherObj.getClass()) {
            return false;
        }
        ScopeKey other = (ScopeKey) otherObj;
        return Objects.equals(this.prefix, other.prefix) && Objects.equals(this.tags, other.tags);
    }

}
