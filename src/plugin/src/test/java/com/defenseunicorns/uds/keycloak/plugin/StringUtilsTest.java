/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StringUtilsTest {

    @Test
    public void shouldParseCommaSeparatedStringIntoTrimmedList() {
        // given
        String input = " one, two ,three ,  four";

        // when
        List<String> result = StringUtils.parseCommaSeparatedStringToList(input);

        // then
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals("one", result.get(0));
        assertEquals("two", result.get(1));
        assertEquals("three", result.get(2));
        assertEquals("four", result.get(3));
    }

    @Test
    public void shouldIgnoreEmptyEntriesBetweenCommas() {
        // given
        String input = ",, a , ,b,, , ,";

        // when
        List<String> result = StringUtils.parseCommaSeparatedStringToList(input);

        // then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("a", result.get(0));
        assertEquals("b", result.get(1));
    }

    @Test
    public void shouldReturnEmptyListForEmptyOrWhitespaceOnlyInput() {
        // given
        String empty = "";
        String spaces = "   \t  \n  ";

        // when
        List<String> emptyResult = StringUtils.parseCommaSeparatedStringToList(empty);
        List<String> spacesResult = StringUtils.parseCommaSeparatedStringToList(spaces);

        // then
        assertNotNull(emptyResult);
        assertTrue(emptyResult.isEmpty());
        assertNotNull(spacesResult);
        assertTrue(spacesResult.isEmpty());
    }

    @Test
    public void shouldHandleSingleValue() {
        // given
        String input = "value";

        // when
        List<String> result = StringUtils.parseCommaSeparatedStringToList(input);

        // then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("value", result.get(0));
    }

    @Test
    public void shouldThrowNPEWhenParsingNullInput() {
        // given
        String input = null;

        // when/then
        assertThrows(NullPointerException.class, () -> {
            StringUtils.parseCommaSeparatedStringToList(input);
        });
    }

    @Test
    public void shouldReturnTrueWhenStringIsNotBlank() {
        // given
        String input = "  content  ";

        // when
        boolean result = StringUtils.isNotBlank(input);

        // then
        assertTrue(result);
    }

    @Test
    public void shouldReturnFalseWhenStringIsBlank() {
        // given
        String input = "   \t  \n  ";

        // when
        boolean result = StringUtils.isNotBlank(input);

        // then
        assertFalse(result);
    }

    @Test
    public void shouldReturnFalseWhenStringIsNull() {
        // given
        String input = null;

        // when
        boolean result = StringUtils.isNotBlank(input);

        // then
        assertFalse(result);
    }
}
