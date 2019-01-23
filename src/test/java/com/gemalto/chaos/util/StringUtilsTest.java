package com.gemalto.chaos.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class StringUtilsTest {
    @Test
    public void addQuotesIfNecessary () {
        assertEquals("abc", StringUtils.addQuotesIfNecessary("abc"));
        assertEquals("\"ab cd\"", StringUtils.addQuotesIfNecessary("ab cd"));
        assertEquals("\"ab cd\"", StringUtils.addQuotesIfNecessary("\"ab cd\""));
    }

    @Test
    public void camelCaseConverter () {
        assertEquals("This Is How It Is", StringUtils.convertCamelCaseToSentence("thisIsHowItIs"));
        assertEquals("Many Many Test Cases", StringUtils.convertCamelCaseToSentence("manyManyTestCases"));
        assertNotEquals("Not Camel case", StringUtils.convertCamelCaseToSentence("notCamcelcase"));
    }
}