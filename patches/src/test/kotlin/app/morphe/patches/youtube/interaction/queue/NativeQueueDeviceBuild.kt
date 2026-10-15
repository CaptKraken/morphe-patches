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
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
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
    val regression = bytecodePatch(name = "Verify local queue playback and manual controls", default = false) {
        dependsOn(nativeQueuePatch)
        execute {
            val runtimeType = "Lapp/morphe/extension/youtube/patches/queue/NativeQueuePatch;"
            val runtime = mutableClassDefBy(runtimeType)
            val playerTypeHook = mutableClassDefBy("Lapp/morphe/extension/youtube/patches/PlayerTypeHookPatch;")
                .methods.single { it.name == "setPlayerType" }.implementation!!.instructions.toList()
            check((playerTypeHook.first() as ReferenceInstruction).reference.toString() ==
                "$runtimeType->onPlayerType(Ljava/lang/Enum;)V") {
                "Queue cleanup must observe the previous player type before the shared helper updates it"
            }
            check(playerTypeHook.filterIsInstance<ReferenceInstruction>().any {
                it.reference.toString().endsWith("PlayerType;->setFromString(Ljava/lang/String;)V")
            }) { "The shared player-type hook must retain its normal update" }
            fun runtimeReferences(name: String) = runtime.methods.single { it.name == name }
                .implementation!!.instructions.filterIsInstance<ReferenceInstruction>().map { it.reference.toString() }
            check(runtimeReferences("onResponse").any { it.contains("LocalQueue;->followPlayback(") } &&
                runtimeReferences("onPlayerMetadata").none { it.contains("LocalQueue;->followPlayback(") }) {
                "Only committed watch navigation may append videos; prefetched metadata cannot grow the queue"
            }
            val reset = runtimeReferences("resetPlayerSession")
            check(reset.any { it.endsWith("LocalQueue;->clear()V") } &&
                listOf("response", "playbackDescriptor", "controller", "navigator").all { field ->
                    reset.any { it.startsWith("$runtimeType->$field:") }
                } && reset.none { it.contains("->publish(") || it.contains("->refreshPanel(") || it.contains("->openVideoIntent") }) {
                "Player close must clear the queue and stale native references without refreshing or reopening a closed player"
            }
            check(runtimeReferences("onRowBound").any { it.contains("LocalQueue;->canRemove(") }) {
                "Playing rows must not offer the custom Remove menu"
            }
            val playerMetadata = mutableClassDefBy("Lapgd;").methods.single { it.name == "setChannelInformation" }
            check((playerMetadata.implementation!!.instructions.first() as ReferenceInstruction)
                .reference.toString().endsWith("NativeQueuePatch;->onPlayerMetadata(Ljava/lang/Object;)V")) {
                "This target needs paired native player title/ID capture despite the generic title-hook version gate"
            }
            val bridge = mutableClassDefBy("Lapp/morphe/extension/youtube/patches/queue/NativeQueueBridge;")
            for ((name, native) in listOf("playerVideo" to "M", "playerTitle" to "L", "playerChannel" to "G")) {
                check(bridge.methods.single { it.name == name }.implementation!!.instructions
                    .filterIsInstance<ReferenceInstruction>().any {
                        it.reference.toString() == "Lahzi;->$native()Ljava/lang/String;"
                    })
            }
            val utils = mutableClassDefBy("Lapp/morphe/extension/youtube/patches/utils/FlyoutUtils;")
            for ((method, callback) in listOf("setFlyoutVideoId" to "onCardMenu",
                "extractFlyoutIdFromObject" to "onTypedMenu")) {
                check(utils.methods.single { it.name == method }.implementation!!.instructions
                    .filterIsInstance<ReferenceInstruction>().any {
                        it.reference.toString().contains("NativeQueuePatch;->$callback(")
                    }) { "Native title capture must survive extension shrinking on both menu paths" }
            }
            val historyModel = mutableClassDefBy("Laznl;")
            check(historyModel.interfaces.any { it.endsWith("FlyoutUtils\$FlyoutMenuVideoIdInterface;") }) {
                "Structured History title capture must match the same renderer used to get the menu's video ID"
            }
            val titleCalls = bridge.methods.single { it.name == "typedMenuTitle" }.implementation!!.instructions
                .filterIsInstance<ReferenceInstruction>().map { it.reference.toString() }
            check("Laznl;->f:Lbbbt;" in titleCalls && "Lapkp;->b(Lbbbt;)Landroid/text/Spanned;" in titleCalls)
            for ((name, member) in listOf("typedMenuChannel" to "g", "typedMenuViews" to "i", "typedMenuAge" to "j")) {
                check(bridge.methods.single { it.name == name }.implementation!!.instructions
                    .filterIsInstance<ReferenceInstruction>().any {
                        it.reference.toString() == "Laznl;->$member:Lbbbt;"
                    }) { "History metadata must use its native structured display fields" }
            }
            val rowReferences = mutableClassDefBy("Loxk;").methods.single { it.name == "gR" }
                .implementation!!.instructions.filterIsInstance<ReferenceInstruction>().map { it.reference.toString() }
            for (field in listOf("Lbfzy;->e:Lbbbt;", "Lbfzy;->y:Lbbbt;",
                "Loxk;->q:Landroid/widget/TextView;", "Loxk;->b:Landroid/widget/TextView;")) {
                check(field in rowReferences) { "Queue metadata must be bound by the native channel/video_info views" }
            }
            fun references(owner: String, name: String) = mutableClassDefBy(owner).methods
                .single { it.name == name }.implementation!!.instructions
                .filterIsInstance<ReferenceInstruction>().map { it.reference.toString() }
            val ratingBind = references("Lozb;", "k")
            for (owner in listOf("Laajo;", "Laajk;")) {
                val account = mutableClassDefBy(owner).methods.single { it.name == "h" }
                    .implementation!!.instructions.toList()
                for (index in account.indices.filter { account[it].opcode == Opcode.RETURN_OBJECT }) {
                    check((account[index - 1] as ReferenceInstruction).reference.toString().endsWith(
                        "NativeQueuePatch;->onRatingAccount(Ljava/lang/Object;)V")) {
                        "Each native current-account return must invalidate the prior account's rating cache"
                    }
                    check((account[index - 1] as RegisterRangeInstruction).startRegister ==
                        (account[index] as OneRegisterInstruction).registerA) {
                        "The account callback must receive the returned identity without altering it"
                    }
                }
            }
            check(ratingBind[0] == "Lozj;->k(Lbhxc;)V" && ratingBind[1].endsWith(
                "NativeQueuePatch;->onRatingPresenter(Ljava/lang/Object;Ljava/lang/Object;)V")) {
                "Local rating events must subscribe after the native model is initialized"
            }
            check("Lahrz;->c()Lahry;" in ratingBind) {
                "The native account entity subscription must remain active"
            }
            val ratingClick = references("Lozb;", "onClick")
            check("Layqy;->k:Lazga;" in ratingClick && "Layqy;->q:Lazga;" in ratingClick &&
                "Lahnn;->c(Lazga;Ljava/util/Map;)V" in ratingClick &&
                ratingClick.none { it.contains("NativeQueue") }) {
                "Native rating buttons must dispatch their default/undo endpoints without custom click handlers"
            }
            val ratingCleanup = references("Lozb;", "b")
            check("Lacpz;->l(Ljava/lang/Object;)V" in ratingCleanup && "Lozb;->n()V" in ratingCleanup) {
                "Native recycling must release rating event and entity subscriptions"
            }
            for ((owner, name, callback) in listOf(
                Triple("Lbdkg;", "getLikeStatus", "onEntityRating"),
                Triple("Laeeo;", "k", "onLegacyRating"),
                Triple("Ljkb;", "<init>", "onVoteEvent")
            )) {
                check(references(owner, name).any { it.contains("NativeQueuePatch;->$callback(") }) {
                    "Initial and live native rating states must reach the queue cache"
                }
            }
            check("Lacpz;->f(Ljava/lang/Object;)V" in references(bridge.type, "registerRatingEvents"))
            val models = references(bridge.type, "panelRatingButtons")
            check("Lbfzy;->z:Laxcf;" in models && "Lbhxi;->b:Laxbm;" in models &&
                "Laxbj;->b(Laxay;)Ljava/lang/Object;" in models) {
                "Ownership must use the actual parsed native button models"
            }
            val localRuntime = "Lapp/morphe/extension/youtube/patches/queue/NativeQueuePatch;"
            check(references(localRuntime, "onRatingPresenter").any {
                it.endsWith("NativeQueueBridge;->registerRatingEvents(Ljava/lang/Object;)V")
            }) { "The local rating subscription hook must survive extension shrinking" }
            for ((owner, literal) in listOf("Lbhxi;" to 124608045L, "Lbdkd;" to 63158558L)) {
                check(mutableClassDefBy(owner).methods.single { it.name == "<clinit>" }
                    .implementation!!.instructions.any { (it as? WideLiteralInstruction)?.wideLiteral == literal }) {
                    "Native rating renderer or command extension changed"
                }
            }
            val command = references("Liaq;", "b")
            check("Lamqr;->z()Z" in command &&
                "Lamrn;->c(Landroid/app/Activity;[BLamrl;)V" in command) {
                "Rating must retain YouTube's account check and sign-in flow"
            }
            check(references("Liaq;", "e").any { it.startsWith("Lbdke;->d(Lbdkj;)") } &&
                references("Liaq;", "e").any { it.startsWith("Lial;-><init>(") }) {
                "The native entity update and failure rollback must remain available"
            }
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
            val headerMenu = references("Lppo;", "d")
            check("Lbfzu;->x:Lbeox;" in headerMenu &&
                "Lppo;->t:Landroid/widget/ImageView;" in headerMenu &&
                "Loyn;-><init>(Ljava/lang/Object;I)V" in headerMenu) {
                "Save must use the native header's three-dot menu and click listener"
            }
            check(references("Loyn;", "onClick").any { it.startsWith("Laqxt;->b(Lcb;Lbeou;") }) {
                "The header menu must retain YouTube's native bottom-sheet presenter"
            }
            check("Lazut;->f:Laxcf;" in references("Ljtu;", "b")) {
                "The native create-playlist command must accept explicit snapshot video IDs"
            }
            val createAccount = references("Ljtu;", "e")
            check("Lamqr;->z()Z" in createAccount &&
                "Lamrn;->c(Landroid/app/Activity;[BLamrl;)V" in createAccount) {
                "Saving must retain native authentication and sign-in"
            }
            val createDialog = references("Ljtu;", "d")
            check("Ljph;-><init>(Lnmg;Landroid/widget/EditText;Lcom/google/android/apps/youtube/app/common/ui/playlist/PrivacySpinner;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Launw;)V" in createDialog &&
                createDialog.any { it.startsWith("Landroid/app/AlertDialog\$Builder;->setPositiveButton(") } &&
                createDialog.none { it.startsWith("Lailt;->j(") }) {
                "Opening Save must show the host's name/privacy dialog before creating anything"
            }
            val createClick = references("Ljph;", "onClick")
            check("Lailn;->J(Ljava/lang/String;)V" in createClick &&
                "Lcom/google/android/apps/youtube/app/common/ui/playlist/PrivacySpinner;->d()I" in createClick &&
                "Lailn;->e(Ljava/lang/String;)V" in createClick &&
                "Lailt;->j(Lailn;Lamtt;)V" in createClick) {
                "Creation must use the native confirmed name, privacy and snapshot videos"
            }
            check(mutableClassDefBy("Lazut;").methods.single { it.name == "<clinit>" }
                .implementation!!.instructions.any { (it as? WideLiteralInstruction)?.wideLiteral == 85650004L }) {
                "The native create-playlist-dialog extension changed"
            }
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
            println("Verified navigation, shuffle/repeat, swipe removal, ratings and native Save playlist menu/dialog")
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
