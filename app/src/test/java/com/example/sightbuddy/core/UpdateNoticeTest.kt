package com.example.sightbuddy.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNoticeTest {

    @Test
    fun `shown after an update`() {
        assertTrue(UpdateNotice.shouldShow(lastSeenVersion = 20, currentVersion = 21, updated = true, aiInstalled = true))
    }

    @Test
    fun `shown on the update from a version before the pop-up existed`() {
        assertTrue(UpdateNotice.shouldShow(lastSeenVersion = null, currentVersion = 20, updated = true, aiInstalled = true))
    }

    @Test
    fun `not shown again in the same version`() {
        assertFalse(UpdateNotice.shouldShow(lastSeenVersion = 20, currentVersion = 20, updated = true, aiInstalled = true))
    }

    @Test
    fun `not shown after a fresh install`() {
        assertFalse(UpdateNotice.shouldShow(lastSeenVersion = null, currentVersion = 20, updated = false, aiInstalled = true))
    }

    @Test
    fun `not shown without the AI on the phone`() {
        assertFalse(UpdateNotice.shouldShow(lastSeenVersion = 20, currentVersion = 21, updated = true, aiInstalled = false))
    }
}
