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
    fun `moving across or moving the playing row preserves its selection and old order`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val insert = type.getMethod("playNext", String::class.java)
        val populated = insert.invoke(insert.invoke(initial, "lmnopqrstuv"), "0123456789_")
        val select = type.getMethod("select", Int::class.javaPrimitiveType)
        val move = type.getMethod("move", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val current = type.getMethod("currentEntry")
        val selection = type.getMethod("selectedIndex")
        for ((from, to) in listOf(0 to 2, 2 to 0, 1 to 2, 1 to 0, 1 to 1)) {
            val playing = select.invoke(populated, 1)
            val moved = move.invoke(playing, from, to)
            assertEquals(current.invoke(playing), current.invoke(moved))
            val expected = videos(playing).toMutableList().apply { add(to, removeAt(from)) }
            assertEquals(expected, videos(moved))
            assertEquals(expected.indexOf("0123456789_"), selection.invoke(moved))
            assertEquals(listOf("abcdefghijk", "0123456789_", "lmnopqrstuv"), videos(playing))
        }
        // Equal video IDs must not confuse which occurrence is currently playing.
        val duplicates = insert.invoke(initial, "abcdefghijk")
        assertEquals(1, selection.invoke(move.invoke(duplicates, 0, 1)))
        for ((from, to) in listOf(-1 to 0, 0 to 3)) {
            val failure = assertFailsWith<InvocationTargetException> { move.invoke(populated, from, to) }
            assertIs<IndexOutOfBoundsException>(failure.cause)
        }
    }

    @Test
    fun `opening an outside video inserts after the playing entry and preserves the remaining queue`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val insert = type.getMethod("playNext", String::class.java)
        val populated = insert.invoke(initial, "lmnopqrstuv")
        val repeated = type.getMethod("withRepeatMode", Int::class.javaPrimitiveType).invoke(populated, 1)
        val open = type.getMethod("playNextAndSelect", String::class.java)
        val opened = open.invoke(repeated, "0123456789_")
        assertEquals(listOf("abcdefghijk", "0123456789_", "lmnopqrstuv"), videos(opened))
        assertEquals(1, type.getMethod("selectedIndex").invoke(opened))
        assertEquals(1, type.getMethod("repeatMode").invoke(opened))
        assertEquals(listOf("abcdefghijk", "lmnopqrstuv"), videos(repeated))
        assertEquals(0, type.getMethod("selectedIndex").invoke(repeated))
        val next = insert.invoke(opened, "ZYXWVUTSRQP")
        assertEquals(listOf("abcdefghijk", "0123456789_", "ZYXWVUTSRQP", "lmnopqrstuv"), videos(next))
        val another = open.invoke(opened, "ZYXWVUTSRQP")
        assertEquals(videos(next), videos(another))
        assertEquals(2, type.getMethod("selectedIndex").invoke(another))
        val failure = assertFailsWith<InvocationTargetException> { open.invoke(opened, "not a video ID") }
        assertIs<IllegalArgumentException>(failure.cause)
        assertEquals(3, videos(opened).size)
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
