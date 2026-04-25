package com.cocode.babakcast.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun parse_acceptsBareSemver() {
        assertEquals(AppVersion(1, 0, 39), AppVersion.parse("1.0.39"))
    }

    @Test
    fun parse_acceptsLeadingV() {
        assertEquals(AppVersion(1, 0, 39), AppVersion.parse("v1.0.39"))
    }

    @Test
    fun parse_acceptsLeadingVUppercase() {
        assertEquals(AppVersion(2, 7, 0), AppVersion.parse("V2.7.0"))
    }

    @Test
    fun parse_treatsTwoComponentsAsPatchZero() {
        // CI default fallback when versionName isn't set is "1.0".
        // Treating that as 1.0.0 lets the in-app check still produce a sane comparison.
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse("1.0"))
    }

    @Test
    fun parse_rejectsGarbage() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("not-a-version"))
        assertNull(AppVersion.parse("1"))
        assertNull(AppVersion.parse("a.b.c"))
    }

    @Test
    fun compareTo_orderingIsSemverNotLexicographic() {
        // 1.0.10 must be greater than 1.0.9 (lexicographic would invert).
        assertTrue(AppVersion(1, 0, 10) > AppVersion(1, 0, 9))
        assertTrue(AppVersion(1, 0, 39) > AppVersion(1, 0, 31))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertTrue(AppVersion(1, 1, 0) > AppVersion(1, 0, 99))
    }

    @Test
    fun compareTo_equalVersionsAreEqual() {
        assertEquals(0, AppVersion(1, 0, 39).compareTo(AppVersion(1, 0, 39)))
    }
}
