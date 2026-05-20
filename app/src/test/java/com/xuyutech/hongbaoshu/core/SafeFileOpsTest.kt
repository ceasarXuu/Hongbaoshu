package com.xuyutech.hongbaoshu.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class SafeFileOpsTest {
    @Test
    fun `moveToBackup moves existing directory without hard delete`() {
        val root = Files.createTempDirectory("safe-file-ops").toFile()
        val target = root.resolve("target").apply { mkdirs() }
        target.resolve("data.txt").writeText("content", StandardCharsets.UTF_8)
        val backups = root.resolve("backups")
        val ops = SafeFileOps(backups)

        val backup = ops.moveToBackup(target, "test")

        assertFalse(target.exists())
        assertTrue(backup?.exists() == true)
        assertTrue(backup!!.resolve("data.txt").exists())
    }

    @Test
    fun `ensureChildPath rejects path traversal`() {
        val root = Files.createTempDirectory("safe-file-ops").toFile()
        val ops = SafeFileOps(root.resolve("backups"))

        val rejected = runCatching {
            ops.ensureChildPath(root, "../escape.txt")
        }.isFailure

        assertTrue(rejected)
    }
}
