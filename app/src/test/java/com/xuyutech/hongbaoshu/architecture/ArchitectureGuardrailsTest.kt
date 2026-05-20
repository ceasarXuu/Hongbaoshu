package com.xuyutech.hongbaoshu.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureGuardrailsTest {
    private val mainSourceDir = File(System.getProperty("user.dir"), "src/main/java")

    @Test
    fun `production code does not call recursive hard delete directly`() {
        val offenders = kotlinFiles()
            .filter { it.readText().contains("deleteRecursively()") }
            .map { it.relativeTo(mainSourceDir).path }

        assertTrue("Use SafeFileOps instead of deleteRecursively(): $offenders", offenders.isEmpty())
    }

    @Test
    fun `production code uses AppLogger instead of direct Android Log calls`() {
        val offenders = kotlinFiles()
            .filter { it.relativeTo(mainSourceDir).invariantSeparatorsPath != "com/xuyutech/hongbaoshu/core/AppLogger.kt" }
            .filter { it.readText().contains("android.util.Log") || it.readText().contains("import android.util.Log") }
            .map { it.relativeTo(mainSourceDir).path }

        assertTrue("Use AppLogger instead of direct android.util.Log calls: $offenders", offenders.isEmpty())
    }

    private fun kotlinFiles(): List<File> {
        return mainSourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }
}
