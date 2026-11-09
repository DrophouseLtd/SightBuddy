package com.example.sightbuddy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VerifiedModelsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private var hashed = 0
    private val verified by lazy {
        VerifiedModels(temp.newFolder("verified")) { hashed++; ModelChecksums.sha256(it) }
    }

    private fun model(text: String = "abc"): File =
        File(temp.root, "models/model.bin").apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    @Test
    fun aMatchingFileIsHashedOnceAndThenRemembered() {
        val file = model()
        assertTrue(verified.check(file, abc))
        assertTrue(verified.check(file, abc))
        assertTrue(verified.isRemembered(file, abc))
        assertEquals(1, hashed)
    }

    @Test
    fun aFileThatDoesNotMatchIsDeletedAndNotRemembered() {
        val file = model("not the model")
        assertFalse(verified.check(file, abc))
        assertFalse(file.exists())
        assertFalse(verified.isRemembered(file, abc))
    }

    @Test
    fun aMissingFileFails() {
        assertFalse(verified.check(File(temp.root, "missing.bin"), abc))
        assertEquals(0, hashed)
    }

    @Test
    fun aFileReplacedSinceItWasCheckedIsHashedAgain() {
        val file = model()
        assertTrue(verified.check(file, abc))
        file.writeText("something else")
        assertFalse(verified.isRemembered(file, abc))
        assertFalse(verified.check(file, abc))
        assertFalse(file.exists())
        assertEquals(2, hashed)
    }

    @Test
    fun aNewPinnedHashChecksTheFileAgain() {
        val file = model()
        assertTrue(verified.check(file, abc))
        assertFalse(verified.check(file, empty))
        assertEquals(2, hashed)
        assertFalse(file.exists())
    }

    @Test
    fun aRememberedFileIsNotHashed() {
        val file = model()
        verified.remember(file, abc)
        assertTrue(verified.check(file, abc))
        assertEquals(0, hashed)
    }

    @Test
    fun aForgottenFileIsHashedAgain() {
        val file = model()
        assertTrue(verified.check(file, abc))
        verified.forget(file)
        assertFalse(verified.isRemembered(file, abc))
        assertTrue(verified.check(file, abc))
        assertEquals(2, hashed)
    }

    @Test
    fun theMemorySurvivesANewInstance() {
        val file = model()
        val dir = temp.newFolder("kept")
        assertTrue(VerifiedModels(dir).check(file, abc))
        var again = 0
        assertTrue(VerifiedModels(dir) { again++; ModelChecksums.sha256(it) }.check(file, abc))
        assertEquals(0, again)
    }

    @Test
    fun matchesHashesNowWhateverCaseThePinIsIn() {
        val file = model()
        assertTrue(verified.matches(file, abc))
        assertTrue(verified.matches(file, abc.uppercase()))
        assertFalse(verified.matches(file, empty))
        // A plain comparison: nothing remembered.
        assertFalse(verified.isRemembered(file, abc))
        assertEquals(3, hashed)
    }
}
