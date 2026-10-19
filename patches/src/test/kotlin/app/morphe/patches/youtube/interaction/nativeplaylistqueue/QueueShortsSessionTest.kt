/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import java.io.File
import java.net.URLClassLoader
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class QueueShortsSessionTest {
    private class Session(type: Class<*>) {
        private val instance = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        private val update = type.getDeclaredMethod("update",
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            String::class.java, String::class.java, String::class.java, Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        private val reset = type.getDeclaredMethod("reset").apply { isAccessible = true }

        fun update(open: Boolean = false, hidden: Boolean = false, visible: Boolean = true,
                   active: String = "abcdefghijk", regular: String = "abcdefghijk", accountPlaylist: Boolean = false) =
            update.invoke(instance, open, hidden, visible, "abcdefghijk", active, regular, accountPlaylist).toString()

        fun reset() { reset.invoke(instance) }
    }

    /** Exercise the actual lifecycle policy, without an imitation Android player. */
    private fun withSession(block: (Session) -> Unit) {
        val source = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/queue/QueueShortsSession.java") }
            .first { it.isFile }
        val output = createTempDirectory("playlist-queue-shorts").toFile()
        try {
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "--release", "17", "-d", output.path, source.path
            ))
            URLClassLoader(arrayOf(output.toURI().toURL()), null).use { loader ->
                block(Session(loader.loadClass("app.morphe.extension.youtube.patches.queue.QueueShortsSession")))
            }
        } finally {
            output.deleteRecursively()
        }
    }

    @Test
    fun `Shorts attachment and hidden player order cannot discard the queue`() = withSession { session ->
        for (hiddenFirst in listOf(true, false)) {
            session.reset()
            assertEquals("NONE", session.update())
            assertEquals("SUSPEND", session.update(open = !hiddenFirst, hidden = hiddenFirst, visible = false))
            assertEquals("WAIT", session.update(open = true, hidden = true, visible = false, active = "lmnopqrstuv"))
            // A later Short may even have the same ID as an entry in the retained queue.
            assertEquals("WAIT", session.update(open = true, active = "abcdefghijk"))
            assertEquals("WAIT", session.update(visible = false, active = "lmnopqrstuv"))
            assertEquals("WAIT", session.update(active = "lmnopqrstuv"))
            assertEquals("RESUME", session.update())
            assertEquals("NONE", session.update())
        }
    }

    @Test
    fun `restored regular ID cannot resume before Shorts detaches and the player returns`() = withSession { session ->
        assertEquals("SUSPEND", session.update(open = true))
        assertEquals("WAIT", session.update(open = true))
        assertEquals("WAIT", session.update(hidden = true, visible = false))
        assertEquals("WAIT", session.update(visible = false))
        assertEquals("RESUME", session.update())
    }

    @Test
    fun `replacement video or account playlist requires agreement with the regular controller`() = withSession { session ->
        for (accountPlaylist in listOf(false, true)) {
            session.reset()
            assertEquals("SUSPEND", session.update(open = true))
            val replacement = if (accountPlaylist) "abcdefghijk" else "lmnopqrstuv"
            assertEquals("WAIT", session.update(open = true, active = replacement,
                regular = replacement, accountPlaylist = accountPlaylist))
            assertEquals("WAIT", session.update(active = replacement, regular = "", accountPlaylist = accountPlaylist))
            assertEquals("RELEASE", session.update(active = replacement,
                regular = replacement, accountPlaylist = accountPlaylist))
        }
    }

    @Test
    fun `missing and stale callbacks wait while a closed player or reset ends the interruption`() = withSession { session ->
        assertEquals("SUSPEND", session.update(hidden = true, visible = false))
        assertEquals("WAIT", session.update(active = ""))
        assertEquals("WAIT", session.update(regular = ""))
        assertEquals("WAIT", session.update(regular = "lmnopqrstuv"))
        assertEquals("RELEASE", session.update(visible = false, active = "", regular = ""))
        assertEquals("SUSPEND", session.update(open = true))
        session.reset()
        assertEquals("NONE", session.update(visible = false, active = "lmnopqrstuv"))
        assertEquals("NONE", session.update())
    }
}
