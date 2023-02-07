package com.uber.m3.tally;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class ScopeKeyTest {
    @Test
    public void testEqualsAndHashCode() {
        EqualsVerifier.forClass(ScopeKey.class).verify();

    }
}
