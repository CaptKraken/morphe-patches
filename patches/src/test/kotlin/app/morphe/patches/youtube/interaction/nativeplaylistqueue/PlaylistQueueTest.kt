/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PlaylistQueueTest {
    /** Exercise the actual Android-free extension state, following the existing Jam test pattern. */
    private fun withQueue(block: (Class<*>) -> Unit) {
        val source = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/PlaylistQueue.java") }
            .first { it.isFile }
        val output = createTempDirectory("playlist-queue-state").toFile()
        try {
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "--release", "17", "-d", output.path, source.path
            ))
            URLClassLoader(arrayOf(output.toURI().toURL()), null).use { loader ->
                block(loader.loadClass("app.morphe.extension.youtube.patches.queue.PlaylistQueue"))
            }
        } finally {
            output.deleteRecursively()
        }
    }

    private fun videos(queue: Any): List<String> = (queue.javaClass.getMethod("entries").invoke(queue) as List<*>)
        .map { entry -> entry!!.javaClass.getMethod("videoId").invoke(entry) as String }

    @Test
    fun `each play next insertion follows the playing video without changing an earlier snapshot`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val insert = type.getMethod("playNext", String::class.java)
        val first = insert.invoke(initial, "lmnopqrstuv")
        val second = insert.invoke(first, "0123456789_")
        assertEquals(listOf("abcdefghijk"), videos(initial))
        assertEquals(listOf("abcdefghijk", "lmnopqrstuv"), videos(first))
        assertEquals(listOf("abcdefghijk", "0123456789_", "lmnopqrstuv"), videos(second))
    }

    @Test
    fun `row selection keeps order and subsequent insertion follows the selected row`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val insert = type.getMethod("playNext", String::class.java)
        val populated = insert.invoke(insert.invoke(initial, "lmnopqrstuv"), "0123456789_")
        val select = type.getMethod("select", Int::class.javaPrimitiveType)
        val selected = select.invoke(populated, 2)
        val updated = insert.invoke(selected, "ZYXWVUTSRQP")
        assertEquals(listOf("abcdefghijk", "0123456789_", "lmnopqrstuv"), videos(selected))
        assertEquals(listOf("abcdefghijk", "0123456789_", "lmnopqrstuv", "ZYXWVUTSRQP"), videos(updated))
        assertEquals(0, type.getMethod("selectedIndex").invoke(populated))
        assertEquals(2, type.getMethod("selectedIndex").invoke(updated))
        for (invalid in listOf(-1, 3)) {
            val failure = assertFailsWith<InvocationTargetException> { select.invoke(populated, invalid) }
            assertIs<IndexOutOfBoundsException>(failure.cause)
        }
    }

    @Test
    fun `removal protects playing entry and preserves its identity when earlier items disappear`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val insert = type.getMethod("playNext", String::class.java)
        val populated = insert.invoke(insert.invoke(initial, "lmnopqrstuv"), "0123456789_")
        val selected = type.getMethod("select", Int::class.javaPrimitiveType).invoke(populated, 1)
        val remove = type.getMethod("remove", Int::class.javaPrimitiveType)
        val protected = remove.invoke(selected, 1)
        assertEquals(videos(selected), videos(protected))
        val earlierRemoved = remove.invoke(selected, 0)
        assertEquals(listOf("0123456789_", "lmnopqrstuv"), videos(earlierRemoved))
        assertEquals(0, type.getMethod("selectedIndex").invoke(earlierRemoved))
        val onlyPlaying = remove.invoke(earlierRemoved, 1)
        assertEquals(listOf("0123456789_"), videos(onlyPlaying))
        assertEquals(listOf("abcdefghijk", "0123456789_", "lmnopqrstuv"), videos(selected))
    }

    @Test
    fun `invalid insertion cannot corrupt a valid queue`() = withQueue { type ->
        val queue = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val failure = assertFailsWith<InvocationTargetException> {
            type.getMethod("playNext", String::class.java).invoke(queue, "not a video ID")
        }
        assertIs<IllegalArgumentException>(failure.cause)
        assertEquals(listOf("abcdefghijk"), videos(queue))
    }
}
