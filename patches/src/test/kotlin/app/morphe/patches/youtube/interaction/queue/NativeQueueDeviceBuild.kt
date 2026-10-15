/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.SdkDexVerifier
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/** Reproducible APK application/SDK verification. This does not establish on-device behavior. */
fun main(arguments: Array<String>) {
    require(arguments.size in 1..2) { "Usage: NativeQueueDeviceBuildKt <youtube-apk> [unsigned-output]" }
    val input = File(arguments[0]).canonicalFile
    require(input.isFile) { "Input APK missing: $input" }
    val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("Native queue validation requires an Android SDK for DEX verification")
    val workspace = Files.createTempDirectory("native-queue-validation").toFile()
    Patcher(PatcherConfig(input, workspace, verifier = SdkDexVerifier(File(sdk)))).use { patcher ->
        patcher += setOf(nativeQueuePatch)
        runBlocking {
            patcher().collect { result ->
                result.exception?.let { throw it }
                println("Applied: ${result.patch.name}")
            }
        }
        val patched = patcher.get()
        arguments.getOrNull(1)?.let { path ->
            val output = File(path).canonicalFile
            output.parentFile.mkdirs()
            patched.applyTo(output)
            println("Unsigned APK: $output")
        }
        println("Native queue patch applied and verified with Android SDK tools: $workspace")
    }
}
