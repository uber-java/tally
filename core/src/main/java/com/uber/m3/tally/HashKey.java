package com.uber.m3.tally;

import com.uber.m3.util.ImmutableMap;

import java.util.Objects;

class HashKey {
    private final String prefix;
    private final ImmutableMap<String, String> tags;

    public HashKey(String prefix, ImmutableMap<String, String> tags) {
        this.prefix = prefix;
        this.tags = tags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, tags);
    }

    @Override
    public boolean equals(Object otherObj) {
        if (this == otherObj)
            return true;
        if (otherObj == null)
            return false;
        if (getClass() != otherObj.getClass())
            return false;
        HashKey other = (HashKey) otherObj;
        return Objects.equals(this.prefix, other.prefix) && Objects.equals(this.tags, other.tags);
    }

}
