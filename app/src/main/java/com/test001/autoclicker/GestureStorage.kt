package com.test001.autoclicker

import android.content.Context
import java.io.File

/** Persists gestures as JSON files under filesDir/gestures/. */
object GestureStorage {
    private fun dir(ctx: Context) = File(ctx.filesDir, "gestures").apply { mkdirs() }

    fun save(ctx: Context, g: Gesture): File {
        val f = File(dir(ctx), "${g.name}.json")
        f.writeText(g.toJson())
        return f
    }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun load(f: File): Gesture? = try { Gesture.fromJson(f.readText()) } catch (_: Throwable) { null }

    fun delete(f: File) { f.delete() }

    fun rename(f: File, newName: String): Boolean {
        val clean = newName.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
        if (clean.isEmpty()) return false
        return f.renameTo(File(f.parentFile, "$clean.json"))
    }
}
