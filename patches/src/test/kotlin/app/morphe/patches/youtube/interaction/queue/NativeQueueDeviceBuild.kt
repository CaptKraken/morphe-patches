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
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
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
    val regression = bytecodePatch(name = "Verify local queue playback and manual controls") {
        dependsOn(nativeQueuePatch)
        execute {
            val dispatch = mutableClassDefBy("Laokg;").methods.single { it.name == "d" }
            val entry = dispatch.implementation!!.instructions.first() as ReferenceInstruction
            check(entry.reference.toString().endsWith("NativeQueuePatch;->preservePlayback()Z")) {
                "Native stop/reopen dispatcher is not guarded before any side effects"
            }
            val install = mutableClassDefBy("Lapp/morphe/extension/youtube/patches/queue/NativeQueueBridge;")
                .methods.single { it.name == "installStorage" }
            val calls = install.implementation!!.instructions.mapNotNull {
                (it as? ReferenceInstruction)?.reference as? MethodReference
            }.map { it.toString() }
            val fill = calls.indexOf("Laojj;->t(Lahwk;)V")
            val attach = calls.indexOf("Laojx;->h(Laoju;)V")
            check(fill >= 0 && attach > fill) {
                "An empty native store would become visible before local rows were loaded"
            }
            val controls = listOf(
                Triple("hD", "onManualNavigation", "Lanug;->c()V"),
                Triple("it", "onManualNavigation", "Lanug;->d()V"),
                Triple("d", "onTransportUpdate", "Laogu;->g()V"),
                Triple("f", "nextEnabled", "Laogt;->iR(Z)V"),
                Triple("g", "previousEnabled", "Laogt;->b(Z)V")
            )
            for ((owner, methods) in listOf(
                "Laogu;" to controls,
                "Llwe;" to listOf(Triple("hD", "onManualNavigation", "Laogu;->hD()V"))
            )) for ((name, callback, original) in methods) {
                val references = mutableClassDefBy(owner).methods.single { it.name == name }
                    .implementation!!.instructions.mapNotNull {
                        ((it as? ReferenceInstruction)?.reference as? MethodReference)?.toString()
                    }
                val hook = references.indexOfFirst { it.contains("NativeQueuePatch;->$callback(") }
                check(hook >= 0 && references.indexOf(original) > hook) {
                    "Overlay $owner->$name must consult the local queue before its original delegate"
                }
            }
            // The stock overlay factory supplies a composite to both possible handlers.
            // A test restricted to hooks in aogu misses the actual registration path.
            val factory = mutableClassDefBy("Lmzp;").methods.single { it.name == "l" }
            check(factory.parameterTypes[2] == "Laogr;")
            val registration = mutableClassDefBy("Lmxy;").methods.single { it.name == "c" }
            val capture = registration.implementation!!.instructions.first()
            check((capture as ReferenceInstruction).reference.toString().endsWith(
                "NativeQueuePatch;->onTransportReady(Ljava/lang/Object;)V")) {
                "Queue ownership must be captured at the actual overlay registration point"
            }
            check((capture as RegisterRangeInstruction).startRegister ==
                    registration.implementation!!.registerCount - 1) {
                "Overlay registration must capture the handler argument, not the controls wrapper"
            }
            val filter = mutableClassDefBy("Lapp/morphe/extension/youtube/patches/queue/NativeQueueBridge;")
                .methods.single { it.name == "isPlayerTransport" }
                .implementation!!.instructions.first() as ReferenceInstruction
            check(filter.reference.toString() == "Laogu;") {
                "Overlay ownership must accept base and subclass handlers regardless of controls wrapper"
            }
            val manual = mutableClassDefBy("Lapp/morphe/extension/youtube/patches/queue/NativeQueueBridge;")
                .methods.single { it.name == "navigateQueue" }
                .implementation!!.instructions.mapNotNull {
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.toString()
                }
            val commit = manual.indexOf("Lniq;->b(Laovl;)Laopa;")
            val load = manual.indexOf("Laowt;->a(Laopa;)V")
            check(commit >= 0 && load > commit && manual.count { it == manual[load] } == 1) {
                "Manual navigation must commit a queue descriptor before loading it once"
            }
            println("Verified playback guard, populated store, composite overlay registration, and both Next implementations")
        }
    }
    Patcher(PatcherConfig(input, workspace, verifier = SdkDexVerifier(File(sdk)))).use { patcher ->
        patcher += setOf(regression)
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
