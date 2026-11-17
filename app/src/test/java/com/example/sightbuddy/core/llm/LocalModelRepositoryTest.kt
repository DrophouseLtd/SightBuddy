package com.example.sightbuddy.core.llm

import com.example.sightbuddy.core.ModelChecksums
import com.example.sightbuddy.core.VerifiedModels
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class LocalModelRepositoryTest {
    @Test fun realDownloadResumesAndVerifiesBeforeInstall() {
        val bytes = "download-fixture-for-two-models".toByteArray()
        val ranges = mutableListOf<String?>()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            val range = e.requestHeaders.getFirst("Range")
            ranges.add(range)
            val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            if (start > 0) e.responseHeaders.add("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}")
            e.sendResponseHeaders(if (start > 0) 206 else 200, (bytes.size - start).toLong())
            e.responseBody.use { it.write(bytes, start, bytes.size - start) }
            e.close()
        }
        server.start()
        try {
            val dir = temp.newFolder("download")
            val spec = LocalModelCatalog.E4B.copy(sizeBytes = bytes.size.toLong(), sha256 = ModelChecksums.sha256(ByteArrayInputStream(bytes)), url = "http://127.0.0.1:${server.address.port}/model")
            val repo = LocalModelRepository(dir, VerifiedModels(temp.newFolder("download-checks")), { null }, {}, listOf(spec))
            repo.part().writeBytes(bytes.copyOf(7))
            assertTrue(repo.download())
            assertEquals("bytes=7-", ranges.single())
            assertArrayEquals(bytes, repo.file().readBytes())
            assertTrue(repo.verified() is com.example.sightbuddy.core.ModelFileGate.ForLoad.Verified)
            assertFalse(repo.part().exists())
            assertTrue(repo.download())
            assertEquals(1, ranges.size)
        } finally { server.stop(0) }
    }

    @Test fun wrongHashAndHttpFailureNeverInstallModel() {
        val bytes = "corrupt".toByteArray()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            if (e.requestURI.path == "/missing") e.sendResponseHeaders(404, -1)
            else {
                e.sendResponseHeaders(200, bytes.size.toLong())
                e.responseBody.use { it.write(bytes) }
            }
            e.close()
        }
        server.start()
        try {
            val spec = LocalModelCatalog.E4B.copy(sizeBytes = bytes.size.toLong(), sha256 = "0".repeat(64), url = "http://127.0.0.1:${server.address.port}/bad")
            for (candidate in listOf(spec, spec.copy(url = "http://127.0.0.1:${server.address.port}/missing"))) {
                val repo = LocalModelRepository(temp.newFolder(), VerifiedModels(temp.newFolder()), { null }, {}, listOf(candidate))
                assertFalse(repo.download())
                assertFalse(repo.isInstalled())
                assertFalse(repo.file().exists())
                assertNull(repo.progress.value)
            }
        } finally { server.stop(0) }
    }

    @Test fun activeDownloadRefusesSelectionAndKeepsOtherModel() {
        val started = java.util.concurrent.CountDownLatch(1)
        val finish = java.util.concurrent.CountDownLatch(1)
        val bytes = "new-model".toByteArray()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            started.countDown()
            finish.await(10, java.util.concurrent.TimeUnit.SECONDS)
            e.sendResponseHeaders(200, bytes.size.toLong())
            e.responseBody.use { it.write(bytes) }
            e.close()
        }
        server.start()
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val spec = LocalModelCatalog.E4B.copy(sizeBytes = bytes.size.toLong(), sha256 = ModelChecksums.sha256(ByteArrayInputStream(bytes)), url = "http://127.0.0.1:${server.address.port}/model")
            val dir = temp.newFolder()
            val old = java.io.File(dir, LocalModelCatalog.E2B.fileName).apply { writeText("keep") }
            val repo = LocalModelRepository(dir, VerifiedModels(temp.newFolder()), { spec.id }, {}, listOf(LocalModelCatalog.E2B, spec))
            val result = pool.submit<Boolean> { repo.download() }
            assertTrue(started.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(repo.select(LocalModelCatalog.E2B.id))
            assertFalse(repo.delete())
            finish.countDown()
            assertTrue(result.get(10, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("keep", old.readText())
            assertTrue(repo.delete())
            assertFalse(repo.file().exists())
            assertEquals("keep", old.readText())
        } finally { finish.countDown(); pool.shutdownNow(); server.stop(0) }
    }

    @get:Rule val temp = TemporaryFolder()

    @Test fun selectionPersistsWithoutRemovingOldFilesOrDownloading() {
        val dir = temp.newFolder("models")
        val bytes = "existing-model".toByteArray()
        val first = LocalModelCatalog.E2B.copy(sizeBytes = bytes.size.toLong(), sha256 = ModelChecksums.sha256(ByteArrayInputStream(bytes)))
        val second = LocalModelCatalog.E4B
        var saved: String? = null
        val checks = VerifiedModels(temp.newFolder("checks"))
        val repo = LocalModelRepository(dir, checks, { saved }, { saved = it }, listOf(first, second))
        assertEquals(first, repo.selected.value)
        java.io.File(dir, first.fileName).writeBytes(bytes)
        assertTrue(repo.isInstalled(first))
        assertTrue(repo.select(second.id))
        assertEquals(second.id, saved)
        assertFalse(repo.isInstalled())
        assertArrayEquals(bytes, java.io.File(dir, first.fileName).readBytes())
        assertEquals(second, LocalModelRepository(dir, checks, { saved }, { saved = it }, listOf(first, second)).selected.value)
        assertFalse(repo.select("invalid"))
        assertEquals(second.id, saved)
    }
}
