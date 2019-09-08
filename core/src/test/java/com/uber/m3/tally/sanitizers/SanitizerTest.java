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

package com.uber.m3.tally.sanitizers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SanitizerTest {

    private static final String NAME = "!@#$%^&*()abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-.";
    private static final String KEY = "!@#$%^&*()abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-.";
    private static final String VALUE = "!@#$%^&*()abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-.";
    private static final String SANITIZED_NAME_1 = "sanitized-name";
    private static final String SANITIZED_KEY_1 = "sanitized-key";
    private static final String SANITIZED_VALUE_1 = "sanitized-value";
    private static final String SANITIZED_NAME_2 = "__________abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890___";
    private static final String SANITIZED_KEY_2 = "__________abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-_";
    private static final String SANITIZED_VALUE_2 = "__________abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-.";
    private static final String SANITIZED_NAME_3 = "@@@@@@@@@@abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_@@";
    private static final String SANITIZED_KEY_3 = "@@@@@@@@@@abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-@";
    private static final String SANITIZED_VALUE_3 = "@@@@@@@@@@abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-.";
    private static final char REPLACEMENT_CHAR = '@';

    @Test
    public void noopSanitizer() {
        Sanitizer sanitizer = new SanitizerBuilder().build();
        assertEquals(NAME, sanitizer.name(NAME));
        assertEquals(KEY, sanitizer.key(KEY));
        assertEquals(VALUE, sanitizer.value(VALUE));
    }

    @Test
    public void withSanitizers() {
        Sanitizer sanitizer =
            new SanitizerBuilder()
                .withNameSanitizer(value -> SANITIZED_NAME_1)
                .withKeySanitizer(value -> SANITIZED_KEY_1)
                .withValueSanitizer(value -> SANITIZED_VALUE_1)
                .build();
        assertEquals(SANITIZED_NAME_1, sanitizer.name(NAME));
        assertEquals(SANITIZED_KEY_1, sanitizer.key(KEY));
        assertEquals(SANITIZED_VALUE_1, sanitizer.value(VALUE));
    }

    @Test
    public void withValidCharactersAndDefaultRepChar() {
        Sanitizer sanitizer =
            new SanitizerBuilder()
                .withNameCharacters(
                    ValidCharacters.of(
                        ValidCharacters.ALPHANUMERIC_RANGE,
                        ValidCharacters.UNDERSCORE_CHARACTERS))
                .withKeyCharacters(
                    ValidCharacters.of(
                        ValidCharacters.ALPHANUMERIC_RANGE,
                        ValidCharacters.UNDERSCORE_DASH_CHARACTERS))
                .withValueCharacters(
                    ValidCharacters.of(
                        ValidCharacters.ALPHANUMERIC_RANGE,
                        ValidCharacters.UNDERSCORE_DASH_DOT_CHARACTERS))
                .build();
        assertEquals(SANITIZED_NAME_2, sanitizer.name(NAME));
        assertEquals(SANITIZED_KEY_2, sanitizer.key(KEY));
        assertEquals(SANITIZED_VALUE_2, sanitizer.value(VALUE));
    }

    @Test
    public void withValidCharactersAndRepChar() {
        Sanitizer sanitizer =
            new SanitizerBuilder()
                .withReplacementCharacter(REPLACEMENT_CHAR)
                .withNameCharacters(
                    ValidCharacters.of(
                        ValidCharacters.ALPHANUMERIC_RANGE,
                        ValidCharacters.UNDERSCORE_CHARACTERS))
                .withKeyCharacters(
                    ValidCharacters.of(
                        ValidCharacters.ALPHANUMERIC_RANGE,
                        ValidCharacters.UNDERSCORE_DASH_CHARACTERS))
                .withValueCharacters(
                    ValidCharacters.of(
                        ValidCharacters.ALPHANUMERIC_RANGE,
                        ValidCharacters.UNDERSCORE_DASH_DOT_CHARACTERS))
                .build();
        assertEquals(SANITIZED_NAME_3, sanitizer.name(NAME));
        assertEquals(SANITIZED_KEY_3, sanitizer.key(KEY));
        assertEquals(SANITIZED_VALUE_3, sanitizer.value(VALUE));
    }
}
