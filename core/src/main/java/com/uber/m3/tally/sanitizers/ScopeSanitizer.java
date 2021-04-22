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

package com.uber.m3.tally.sanitizers;

/**
 * SanitizerImpl sanitizes scope properties: name, key and value
 * for counter/timer/histogram/gauge/tags/etc
 */
public interface ScopeSanitizer {

    /**
     * Name sanitizes the provided 'name' of counter/timer/histogram/gauge/etc
     * @param name the name string
     * @return the sanitized name
     */
    String sanitizeName(String name);

    /**
     * Key sanitizes the provided 'key' of a tag
     * @param key the key string
     * @return the sanitized key
     */
    String sanitizeTagKey(String key);

    /**
     * Value sanitizes the provided 'value' of a tag
     * @param value the value string
     * @return the sanitized value
     */
    String sanitizeTagValue(String value);
}
