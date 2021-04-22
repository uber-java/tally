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

import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class ScopeSanitizerTest {

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
    public void builderNoopSanitizer() {
        ScopeSanitizer sanitizer = new ScopeSanitizerBuilder().build();
        assertEquals(NAME, sanitizer.sanitizeName(NAME));
        assertEquals(KEY, sanitizer.sanitizeTagKey(KEY));
        assertEquals(VALUE, sanitizer.sanitizeTagValue(VALUE));
    }

    @Test
    public void noopSanitizer() {
        ScopeSanitizer sanitizer = new NoopSanitizer();
        assertEquals(NAME, sanitizer.sanitizeName(NAME));
        assertEquals(KEY, sanitizer.sanitizeTagKey(KEY));
        assertEquals(VALUE, sanitizer.sanitizeTagValue(VALUE));
    }

    @Test
    public void withSanitizers() {
        ScopeSanitizer sanitizer =
                new ScopeSanitizerBuilder()
                        .withNameSanitizer(value -> SANITIZED_NAME_1)
                        .withTagKeySanitizer(value -> SANITIZED_KEY_1)
                        .withTagValueSanitizer(value -> SANITIZED_VALUE_1)
                        .build();
        assertEquals(SANITIZED_NAME_1, sanitizer.sanitizeName(NAME));
        assertEquals(SANITIZED_KEY_1, sanitizer.sanitizeTagKey(KEY));
        assertEquals(SANITIZED_VALUE_1, sanitizer.sanitizeTagValue(VALUE));
    }

    @Test
    public void withValidCharactersAndDefaultRepChar() {
        ScopeSanitizer sanitizer =
                new ScopeSanitizerBuilder()
                        .withNameValidCharacters(
                                ValidCharacters.of(
                                        ValidCharacters.ALPHANUMERIC_RANGE,
                                        ValidCharacters.UNDERSCORE_CHARACTERS))
                        .withTagKeyValidCharacters(
                                ValidCharacters.of(
                                        ValidCharacters.ALPHANUMERIC_RANGE,
                                        ValidCharacters.UNDERSCORE_DASH_CHARACTERS))
                        .withTagValueValidCharacters(
                                ValidCharacters.of(
                                        ValidCharacters.ALPHANUMERIC_RANGE,
                                        ValidCharacters.UNDERSCORE_DASH_DOT_CHARACTERS))
                        .build();
        assertEquals(SANITIZED_NAME_2, sanitizer.sanitizeName(NAME));
        assertEquals(SANITIZED_KEY_2, sanitizer.sanitizeTagKey(KEY));
        assertEquals(SANITIZED_VALUE_2, sanitizer.sanitizeTagValue(VALUE));
    }

    @Test
    public void withValidCharactersAndRepChar() {
        ScopeSanitizer sanitizer =
                new ScopeSanitizerBuilder()
                        .withReplacementCharacter(REPLACEMENT_CHAR)
                        .withNameValidCharacters(
                                ValidCharacters.of(
                                        ValidCharacters.ALPHANUMERIC_RANGE,
                                        ValidCharacters.UNDERSCORE_CHARACTERS))
                        .withTagKeyValidCharacters(
                                ValidCharacters.of(
                                        ValidCharacters.ALPHANUMERIC_RANGE,
                                        ValidCharacters.UNDERSCORE_DASH_CHARACTERS))
                        .withTagValueValidCharacters(
                                ValidCharacters.of(
                                        ValidCharacters.ALPHANUMERIC_RANGE,
                                        ValidCharacters.UNDERSCORE_DASH_DOT_CHARACTERS))
                        .build();
        assertEquals(SANITIZED_NAME_3, sanitizer.sanitizeName(NAME));
        assertEquals(SANITIZED_KEY_3, sanitizer.sanitizeTagKey(KEY));
        assertEquals(SANITIZED_VALUE_3, sanitizer.sanitizeTagValue(VALUE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidBuilderConflictNameSanitizer() {
        new ScopeSanitizerBuilder()
                .withReplacementCharacter(REPLACEMENT_CHAR)
                .withNameValidCharacters(
                        ValidCharacters.of(
                                ValidCharacters.ALPHANUMERIC_RANGE,
                                ValidCharacters.UNDERSCORE_CHARACTERS))
                .withNameSanitizer(Function.identity())
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidBuilderConflictTagKeySanitizer() {
        new ScopeSanitizerBuilder()
                .withReplacementCharacter(REPLACEMENT_CHAR)
                .withTagKeyValidCharacters(
                        ValidCharacters.of(
                                ValidCharacters.ALPHANUMERIC_RANGE,
                                ValidCharacters.UNDERSCORE_DASH_CHARACTERS))
                .withTagKeySanitizer(Function.identity())
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidBuilderConflictTagValueSanitizer() {
        new ScopeSanitizerBuilder()
                .withReplacementCharacter(REPLACEMENT_CHAR)
                .withTagValueValidCharacters(
                        ValidCharacters.of(
                                ValidCharacters.ALPHANUMERIC_RANGE,
                                ValidCharacters.UNDERSCORE_DASH_CHARACTERS))
                .withTagValueSanitizer(Function.identity())
                .build();
    }
}
