package com.xuyutech.hongbaoshu.core

import java.io.File

class SafeFileOps(
    private val backupRoot: File
) {
    fun moveToBackup(target: File, reason: String): File? {
        if (!target.exists()) return null
        backupRoot.mkdirs()
        val backup = uniqueBackupFile(target.name, reason)
        backup.parentFile?.mkdirs()
        if (target.renameTo(backup)) return backup
        copyRecursively(target, backup)
        require(target.renameTo(uniqueBackupFile("${target.name}_moved", reason))) {
            "Failed to move ${target.absolutePath} to backup"
        }
        return backup
    }

    fun atomicReplace(stagingDir: File, targetDir: File, reason: String) {
        targetDir.parentFile?.mkdirs()
        moveToBackup(targetDir, reason)
        if (!stagingDir.renameTo(targetDir)) {
            copyRecursively(stagingDir, targetDir)
            moveToBackup(stagingDir, "${reason}_staging")
        }
    }

    fun ensureChildPath(root: File, relativePath: String): File {
        val rootCanonical = root.canonicalFile
        val output = File(rootCanonical, relativePath).canonicalFile
        require(output.path == rootCanonical.path || output.path.startsWith(rootCanonical.path + File.separator)) {
            "Path escapes target root: $relativePath"
        }
        return output
    }

    private fun uniqueBackupFile(name: String, reason: String): File {
        val safeReason = reason.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val stamp = System.currentTimeMillis()
        return File(backupRoot, "${stamp}_${safeReason}_$name")
    }

    private fun copyRecursively(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyRecursively(child, File(dest, child.name))
            }
        } else {
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
        }
    }
}
