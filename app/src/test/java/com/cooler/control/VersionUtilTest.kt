package com.cooler.control

import com.cooler.control.update.VersionUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilTest {

    @Test
    fun newerPatch() {
        assertTrue(VersionUtil.isNewer("0.19.1", "0.19.0"))
    }

    @Test
    fun olderIsNotNewer() {
        assertFalse(VersionUtil.isNewer("0.18.9", "0.19.0"))
    }

    @Test
    fun sameVersion() {
        assertFalse(VersionUtil.isNewer("0.19.0", "0.19.0"))
    }

    @Test
    fun minorBeatsPatch() {
        assertTrue(VersionUtil.isNewer("1.0.0", "0.99.99"))
    }

    @Test
    fun differentLength() {
        assertTrue(VersionUtil.isNewer("1.0.1", "1.0"))
        assertFalse(VersionUtil.isNewer("1.0", "1.0.0"))
    }
}
