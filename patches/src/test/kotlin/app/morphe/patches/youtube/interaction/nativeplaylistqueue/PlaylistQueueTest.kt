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
                null, null, null, "--release", "17", "-d", output.path, source.path,
                File(source.parentFile, "QueueAdvance.java").path
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
    fun `one completion cannot dispatch twice or target a reopened duplicate entry`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "abcdefghijk")
        val queue = type.getMethod("playNext", String::class.java).invoke(initial, "abcdefghijk")
        val completion = type.classLoader.loadClass("app.morphe.extension.youtube.patches.queue.QueueAdvance")
        val constructor = completion.getDeclaredConstructor(type, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        fun call(instance: Any, name: String, source: Any): Any? = completion.getDeclaredMethod(name, type)
            .apply { isAccessible = true }.invoke(instance, source)
        val ended = constructor.newInstance(queue, false)
        assertEquals(true, call(ended, "beginRequest", queue))
        assertEquals(false, call(ended, "beginRequest", queue))
        val target = call(ended, "takePlaybackTarget", queue)
        assertEquals("abcdefghijk", target!!.javaClass.getMethod("videoId").invoke(target))
        assertEquals(null, call(ended, "takePlaybackTarget", queue))
        val selected = type.getMethod("select", Int::class.javaPrimitiveType).invoke(queue, 1)
        assertEquals(false, call(ended, "isFor", selected))
        val reopened = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "abcdefghijk")
        assertEquals(false, call(ended, "isFor", reopened))
    }

    @Test
    fun `completion follows reordered queue and stops at its end or an end sleep timer`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val insert = type.getMethod("playNext", String::class.java)
        val queue = insert.invoke(insert.invoke(initial, "lmnopqrstuv"), "0123456789_")
        val completion = type.classLoader.loadClass("app.morphe.extension.youtube.patches.queue.QueueAdvance")
        val constructor = completion.getDeclaredConstructor(type, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        fun call(instance: Any, name: String, source: Any): Any? = completion.getDeclaredMethod(name, type)
            .apply { isAccessible = true }.invoke(instance, source)
        val ended = constructor.newInstance(queue, false)
        val moved = type.getMethod("move", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(queue, 2, 1)
        val target = call(ended, "takePlaybackTarget", moved)
        assertEquals("lmnopqrstuv", target!!.javaClass.getMethod("videoId").invoke(target))
        assertEquals(0, type.getMethod("selectedIndex").invoke(moved))
        val last = type.getMethod("select", Int::class.javaPrimitiveType).invoke(queue, 2)
        assertEquals(null, call(constructor.newInstance(last, false), "takePlaybackTarget", last))
        val timer = constructor.newInstance(queue, true)
        assertEquals(false, call(timer, "beginRequest", queue))
        assertEquals(null, call(timer, "takePlaybackTarget", queue))
    }

    @Test
    fun `repeat distinguishes automatic replay from manual skips and survives queue edits`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val insert = type.getMethod("playNext", String::class.java)
        val queue = insert.invoke(insert.invoke(initial, "lmnopqrstuv"), "0123456789_")
        val select = type.getMethod("select", Int::class.javaPrimitiveType)
        val repeat = type.getMethod("withRepeatMode", Int::class.javaPrimitiveType)
        val next = type.getMethod("nextIndex", Boolean::class.javaPrimitiveType)
        val previous = type.getMethod("previousIndex")
        for ((mode, automatic, manual, backwards) in listOf(
            listOf(0, -1, -1, -1), listOf(1, 0, 0, 2), listOf(2, 2, -1, -1)
        )) {
            val repeated = repeat.invoke(queue, mode)
            val last = select.invoke(repeated, 2)
            assertEquals(automatic, next.invoke(last, true))
            assertEquals(manual, next.invoke(last, false))
            assertEquals(backwards, previous.invoke(repeated))
            var edited = type.getMethod("move", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(last, 0, 1)
            edited = type.getMethod("remove", Int::class.javaPrimitiveType).invoke(edited, 0)
            edited = insert.invoke(edited, "ZYXWVUTSRQP")
            assertEquals(mode, type.getMethod("repeatMode").invoke(edited))
            assertEquals("lmnopqrstuv", type.getMethod("currentEntry").invoke(edited)
                .javaClass.getMethod("videoId").invoke(type.getMethod("currentEntry").invoke(edited)))
        }
        assertEquals(0, type.getMethod("repeatMode").invoke(queue))
        val single = repeat.invoke(initial, 1)
        assertEquals(0, next.invoke(single, true))
        assertEquals(0, next.invoke(single, false))
        assertEquals(0, previous.invoke(single))
        for (invalid in listOf(-1, 3)) {
            val failure = assertFailsWith<InvocationTargetException> { repeat.invoke(queue, invalid) }
            assertIs<IllegalArgumentException>(failure.cause)
        }
    }

    @Test
    fun `native repeat one retains completion ownership while off and all still advance locally`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val queue = type.getMethod("playNext", String::class.java).invoke(initial, "lmnopqrstuv")
        val repeat = type.getMethod("withRepeatMode", Int::class.javaPrimitiveType)
        val completion = type.classLoader.loadClass("app.morphe.extension.youtube.patches.queue.QueueAdvance")
        val constructor = completion.getDeclaredConstructor(type, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        fun call(instance: Any, name: String, source: Any): Any? = completion.getDeclaredMethod(name, type)
            .apply { isAccessible = true }.invoke(instance, source)
        for (index in listOf(0, 1)) {
            val selected = type.getMethod("select", Int::class.javaPrimitiveType).invoke(queue, index)
            val one = repeat.invoke(selected, 2)
            val ended = constructor.newInstance(one, false)
            assertEquals(false, call(ended, "beginRequest", one))
            assertEquals(null, call(ended, "takePlaybackTarget", one))
            assertEquals(false, call(constructor.newInstance(one, true), "beginRequest", one))
        }
        for (mode in listOf(0, 1)) {
            val source = repeat.invoke(queue, mode)
            val ended = constructor.newInstance(source, false)
            assertEquals(true, call(ended, "beginRequest", source))
            val target = call(ended, "takePlaybackTarget", source)!!
            assertEquals("lmnopqrstuv", target.javaClass.getMethod("videoId").invoke(target))
        }
    }

    @Test
    fun `changing repeat after the end re-arms one completion but keeps the sleep timer authoritative`() = withQueue { type ->
        val initial = type.getConstructor(String::class.java, String::class.java)
            .newInstance("abcdefghijk", "Playing video")
        val queue = type.getMethod("playNext", String::class.java).invoke(initial, "lmnopqrstuv")
        val last = type.getMethod("select", Int::class.javaPrimitiveType).invoke(queue, 1)
        val completion = type.classLoader.loadClass("app.morphe.extension.youtube.patches.queue.QueueAdvance")
        val constructor = completion.getDeclaredConstructor(type, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        fun call(instance: Any, name: String, source: Any): Any? = completion.getDeclaredMethod(name, type)
            .apply { isAccessible = true }.invoke(instance, source)
        val changed = completion.getDeclaredMethod("repeatChanged").apply { isAccessible = true }
        val ended = constructor.newInstance(last, false)
        assertEquals(null, call(ended, "takePlaybackTarget", last))
        val one = type.getMethod("withRepeatMode", Int::class.javaPrimitiveType).invoke(last, 2)
        changed.invoke(ended)
        assertEquals(false, call(ended, "beginRequest", one))
        assertEquals(null, call(ended, "takePlaybackTarget", one))
        val all = type.getMethod("withRepeatMode", Int::class.javaPrimitiveType).invoke(last, 1)
        changed.invoke(ended)
        val first = (type.getMethod("entries").invoke(all) as List<*>).first()
        assertEquals(first, call(ended, "takePlaybackTarget", all))
        val timer = constructor.newInstance(all, true)
        changed.invoke(timer)
        assertEquals(false, call(timer, "beginRequest", all))
        assertEquals(null, call(timer, "takePlaybackTarget", all))
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
