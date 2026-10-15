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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
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
            val shuffleConstructor = calls.indexOf("Lnig;-><init>(Lnii;Laobb;Lbpv;Lisl;Laooh;)V")
            val backendConstructor = calls.indexOf("Laokc;-><init>(Laojo;Laokg;)V")
            val switchBackend = calls.indexOf("Lniq;->patch_restoreLocalQueueBackend(Laovn;)V")
            check(shuffleConstructor >= 0 && shuffleConstructor < fill &&
                    backendConstructor > attach && switchBackend > backendConstructor) {
                "The populated local store must use the native shuffle/repeat navigator"
            }
            val modeCalls = mutableClassDefBy("Lapp/morphe/extension/youtube/patches/queue/NativeQueueBridge;")
                .methods.single { it.name == "setPlaybackModes" }.implementation!!.instructions
                .filterIsInstance<ReferenceInstruction>().map { it.reference.toString() }
            check(modeCalls.indexOf("Lnig;->a:[I") < modeCalls.lastIndexOf("Lnig;->f(Z)V") &&
                    modeCalls.indexOf("Lnig;->b:[I") < modeCalls.lastIndexOf("Lnig;->f(Z)V") &&
                    modeCalls.lastIndexOf("Lnig;->f(Z)V") < modeCalls.indexOf("Laokc;->q(I)V")) {
                "Rebuild random navigation before notifying observers of mode/availability changes"
            }
            for ((name, callback) in listOf("q" to "onRepeatRequested", "f" to "onShuffleRequested")) {
                val implementation = mutableClassDefBy("Lniq;").methods.single { it.name == name }
                    .implementation!!
                check(implementation.registerCount >= 3) {
                    "The mode forwarder needs a result register that does not alias its receiver"
                }
                val first = implementation.instructions.first() as ReferenceInstruction
                check(first.reference.toString().contains("NativeQueuePatch;->$callback("))
                val delegate = if (name == "q") "Laovj;->q(I)V" else "Laovo;->f(Z)V"
                check(implementation.instructions.filterIsInstance<ReferenceInstruction>()
                    .last().reference.toString() == delegate) {
                    "Normal account-playlist mode requests must retain their native delegate"
                }
            }
            val headerBind = mutableClassDefBy("Lppo;").methods.single { it.name == "d" }
                .implementation!!.instructions.toList()
            for (index in headerBind.indices.filter { headerBind[it].opcode == Opcode.RETURN_VOID }) {
                check((headerBind[index - 1] as ReferenceInstruction).reference.toString().contains(
                    "NativeQueuePatch;->onQueueHeader("))
            }
            val headerControls = mutableClassDefBy("Lapp/morphe/extension/youtube/patches/queue/NativeQueueBridge;")
                .methods.single { it.name == "enableHeaderControls" }.implementation!!.instructions
                .filterIsInstance<ReferenceInstruction>().map { it.reference.toString() }
            check("Lppo;->u:Landroid/view/View\$OnClickListener;" in headerControls &&
                    "Lppo;->v:Landroid/view/View\$OnClickListener;" in headerControls &&
                    "Lppo;->k(IZ)V" in headerControls) {
                "Local mode controls must retain native listeners and native selected-state rendering"
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
            for ((name, hostLifecycle) in listOf("gR" to "d", "oN" to "e")) {
                val references = mutableClassDefBy("Loxk;").methods.single { it.name == name }
                    .implementation!!.instructions.filterIsInstance<ReferenceInstruction>()
                    .map { it.reference.toString() }
                check(references.any { it.startsWith("Lolb;->$hostLifecycle(") }) {
                    "Swipe buttons must retain YouTube's native presenter $name lifecycle"
                }
            }
            for ((owner, name, literal) in listOf(
                Triple("Lngw;", "a", 65153809L),
                Triple("Lbbmu;", "<clinit>", 77297742L)
            )) {
                check(mutableClassDefBy(owner).methods.single { it.name == name }
                    .implementation!!.instructions.any {
                        (it as? WideLiteralInstruction)?.wideLiteral == literal
                    }) { "Native swipe renderer or client-only hide-command tag changed" }
            }
            val hideCalls = mutableClassDefBy("Lidg;").methods.single { it.name == "b" }
                .implementation!!.instructions.filterIsInstance<ReferenceInstruction>()
                .map { it.reference.toString() }
            check("Lahmg;-><init>(Ljava/lang/Object;)V" in hideCalls) {
                "The native hide command must emit the tagged-row event intercepted by the queue"
            }
            println("Verified playback/navigation guards, native shuffle/repeat controls, and native swipe removal")
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
