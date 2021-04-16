// Copyright (c) 2020 Uber Technologies, Inc.
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
 * SanitizerImpl sanitizes the provided input based on the function executed.
 */
class SanitizerImpl implements ScopeSanitizer {

    private final StringSanitizer nameSanitizer;
    private final StringSanitizer keySanitizer;
    private final StringSanitizer valueSanitizer;

    SanitizerImpl(StringSanitizer nameSanitizer, StringSanitizer keySanitizer, StringSanitizer valueSanitizer) {
        this.nameSanitizer = nameSanitizer;
        this.keySanitizer = keySanitizer;
        this.valueSanitizer = valueSanitizer;
    }

    /**
     * Name sanitizes the provided 'name' string.
     * @param name the name string
     * @return the sanitized name
     */
    @Override
    public String sanitizeName(String name) {
        return this.nameSanitizer.sanitize(name);
    }

    /**
     * Key sanitizes the provided 'key' string.
     * @param key the key string
     * @return the sanitized key
     */
    @Override
    public String sanitizeTagKey(String key) {
        return this.keySanitizer.sanitize(key);
    }

    /**
     * Value sanitizes the provided 'value' string.
     * @param value the value string
     * @return the sanitized value
     */
    @Override
    public String sanitizeTagValue(String value) {
        return this.valueSanitizer.sanitize(value);
    }
}
