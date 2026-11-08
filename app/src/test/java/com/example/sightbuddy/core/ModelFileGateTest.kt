package com.example.sightbuddy.core

import com.example.sightbuddy.core.ModelFileGate.ForLoad
import com.example.sightbuddy.core.ModelFileGate.Install
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ModelFileGateTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private val checks by lazy { VerifiedModels(temp.newFolder("verified")) }

    /** A store whose hashing fails as a file that cannot be read does. */
    private val unreadable by lazy {
        VerifiedModels(temp.newFolder("verified-unreadable")) { throw IOException("read error") }
    }

    private fun file(name: String, text: String): File = File(temp.root, name).apply { writeText(text) }

    // Before a load.

    @Test
    fun theRightFileIsLetThroughForLoading() {
        val model = file("model.bin", "abc")
        val result = ModelFileGate.forLoad(model, abc, checks)
        assertTrue(result is ForLoad.Verified)
        assertEquals(model, (result as ForLoad.Verified).file)
        assertTrue(checks.isRemembered(model, abc))
    }

    @Test
    fun aWrongFileIsNeverLoadedAndIsDeleted() {
        val model = file("model.bin", "tampered")
        assertEquals(ForLoad.Damaged, ModelFileGate.forLoad(model, abc, checks))
        assertFalse(model.exists())
        assertFalse(checks.isRemembered(model, abc))
    }

    @Test
    fun aMissingFileIsNotLoaded() {
        assertEquals(ForLoad.Missing, ModelFileGate.forLoad(File(temp.root, "none.bin"), abc, checks))
    }

    @Test
    fun aFileThatCannotBeReadIsNotVerifiedAndIsKept() {
        val model = file("model.bin", "abc")
        assertTrue(ModelFileGate.forLoad(model, abc, unreadable) is ForLoad.Unreadable)
        assertTrue(model.exists())
        assertFalse(unreadable.isRemembered(model, abc))
    }

    // After a download.

    @Test
    fun theRightDownloadIsMovedIntoPlaceAndRemembered() {
        val part = file("model.bin.part", "abc")
        val target = File(temp.root, "model.bin")
        assertEquals(Install.INSTALLED, ModelFileGate.install(part, target, abc, checks))
        assertEquals("abc", target.readText())
        assertFalse(part.exists())
        assertTrue(checks.isRemembered(target, abc))
    }

    @Test
    fun aWrongDownloadIsNeverMovedIntoPlaceAndIsDeleted() {
        val part = file("model.bin.part", "tampered")
        val target = File(temp.root, "model.bin")
        assertEquals(Install.WRONG_FILE, ModelFileGate.install(part, target, abc, checks))
        assertFalse(target.exists())
        assertFalse(part.exists())
    }

    @Test
    fun aDownloadThatCannotBeReadIsNotMovedIntoPlace() {
        val part = file("model.bin.part", "abc")
        val target = File(temp.root, "model.bin")
        try {
            ModelFileGate.install(part, target, abc, unreadable)
            fail("a read error must not count as checked")
        } catch (expected: IOException) {
        }
        assertFalse(target.exists())
    }
}
