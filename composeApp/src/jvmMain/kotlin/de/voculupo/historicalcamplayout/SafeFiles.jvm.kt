package de.voculupo.historicalcamplayout

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Same code as SafeFiles.android.kt: both platforms use java.io, but the default KMP hierarchy has no
// shared android+jvm source set. Keep the two copies identical.
internal object SafeFiles {
    /**
     * Writes [text] to a temp file and atomically moves it over [file], so a crash mid-write never
     * leaves a half-written project. The previous version is kept as `<name>.bak`.
     * Synchronized because autosave (background) and manual save (UI thread) can overlap.
     */
    @Synchronized
    fun write(
        file: File,
        text: String,
    ) {
        val dir = file.absoluteFile.parentFile.also { it.mkdirs() }
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(text)
        if (file.exists()) {
            Files.move(file.toPath(), backupOf(file).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /**
     * Reads and parses [file], falling back to its backup. A file that exists but can't be parsed is
     * moved aside as `<name>.corrupt-<timestamp>` instead of being silently overwritten by the next save.
     */
    fun <T> read(
        file: File,
        parse: (String) -> T,
    ): T? {
        if (file.exists()) {
            runCatching { parse(file.readText()) }.getOrNull()?.let { return it }
            val corrupt = File(file.absoluteFile.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            runCatching { Files.move(file.toPath(), corrupt.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
        val backup = backupOf(file)
        if (!backup.exists()) return null
        return runCatching { parse(backup.readText()) }.getOrNull()
    }

    private fun backupOf(file: File) = File(file.absoluteFile.parentFile, "${file.name}.bak")
}
