// Copyright (c) Microsoft. All rights reserved.
package com.microsoft.semantickernel.data.recorddefinition;

public enum DistanceFunction {
    COSINE_SIMILARITY("cosineSimilarity"), DOT_PRODUCT("dotProduct"), EUCLIDEAN("euclidean");

    private final String value;

    DistanceFunction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DistanceFunction fromString(String text) {
        if (text == null || text.isEmpty()) {
            return COSINE_SIMILARITY;
        }

        for (DistanceFunction b : DistanceFunction.values()) {
            if (b.value.equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("No distance function with value " + text + " found");
    }
}
