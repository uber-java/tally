package com.uber.m3.tally;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

/**
 * Tests for {@link  com.uber.m3.tally.ScopeKey}
 **/
public class ScopeKeyTest {
    @Test
    public void testEqualsAndHashCode() {
        EqualsVerifier.forClass(ScopeKey.class).verify();
    }
}
