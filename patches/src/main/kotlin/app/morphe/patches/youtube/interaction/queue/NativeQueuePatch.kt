/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.queue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patches.youtube.layout.flyout.addToQueuePatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EXTENSION = "Lapp/morphe/extension/youtube/patches/queue/NativeQueuePatch;"
private const val BRIDGE = "Lapp/morphe/extension/youtube/patches/queue/NativeQueueBridge;"

/**
 * Deliberately pinned prototype ABI. Do not advertise other versions until their native panel,
 * navigator and edit paths have been resolved and verified. Every accessed member is checked.
 */
@Suppress("unused")
val nativeQueuePatch = bytecodePatch(
    name = "Native temporary queue (experimental)",
    description = "Uses the native playlist panel for a temporary local queue. YouTube 21.20.405 only.",
    default = false,
) {
    dependsOn(addToQueuePatch)
    compatibleWith(Compatibility(
        name = requireNotNull(COMPATIBILITY_YOUTUBE.name),
        packageName = requireNotNull(COMPATIBILITY_YOUTUBE.packageName),
        apkFileType = COMPATIBILITY_YOUTUBE.apkFileType,
        signatures = COMPATIBILITY_YOUTUBE.signatures,
        appIconColor = COMPATIBILITY_YOUTUBE.appIconColor,
        targets = COMPATIBILITY_YOUTUBE.targets.filter { it.version == "21.20.405" }
    ))

    execute {
        if (packageMetadata.versionName != "21.20.405") {
            throw PatchException("Native temporary queue currently requires YouTube 21.20.405")
        }

        fun method(type: String, name: String, parameters: List<String>, result: String) =
            mutableClassDefBy(type).methods.singleOrNull {
                it.name == name && it.parameterTypes == parameters && it.returnType == result
            } ?: throw PatchException("Unsupported native queue ABI: $type->$name$parameters$result")

        fun field(type: String, name: String, valueType: String, writable: Boolean = false): String {
            val f = mutableClassDefBy(type).fields.singleOrNull { it.name == name && it.type == valueType }
                ?: throw PatchException("Unsupported native queue field: $type->$name:$valueType")
            f.accessFlags = (f.accessFlags and (AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value).inv()) or
                    AccessFlags.PUBLIC.value
            if (writable) f.accessFlags = f.accessFlags and AccessFlags.FINAL.value.inv()
            return "$type->$name:$valueType"
        }

        fun bridge(name: String, body: String) {
            val owner = mutableClassDefBy(BRIDGE)
            val target = owner.methods.single { it.name == name }
            owner.methods.remove(target)
            owner.methods.add(ImmutableMethod(
                BRIDGE, name, target.parameters, target.returnType, target.accessFlags,
                target.annotations, target.hiddenApiRestrictions, MutableMethodImplementation(12)
            ).toMutable().apply { addInstructions(0, body) })
        }

        fun getter(name: String, owner: String, member: String, type: String) {
            val reference = field(owner, member, type)
            bridge(name, """
                check-cast p0, $owner
                iget-object v0, p0, $reference
                return-object v0
            """)
        }

        fun setter(name: String, owner: String, member: String, type: String) {
            val reference = field(owner, member, type, true)
            bridge(name, """
                check-cast p0, $owner
                check-cast p1, $type
                iput-object p1, p0, $reference
                return-void
            """)
        }

        // Decode using the host runtime and its extension registry. The protected parser is
        // called from a subclass, rather than relying on reflection or illegal DEX access.
        method("Laxbo;", "parseFrom", listOf("Laxbo;", "[B", "Lcom/google/protobuf/ExtensionRegistryLite;"), "Laxbo;")
        method("Lcom/google/protobuf/ExtensionRegistryLite;", "getGeneratedRegistry", emptyList(), "Lcom/google/protobuf/ExtensionRegistryLite;")
        val defaultPanel = field("Lbfzu;", "a", "Lbfzu;")
        mutableClassDefBy("Lbfzu;").methods.add(ImmutableMethod(
            "Lbfzu;", "patch_parseLocalQueue", listOf(ImmutableMethodParameter("[B", null, null)),
            "Ljava/lang/Object;", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null, null, MutableMethodImplementation(4)
        ).toMutable().apply {
            addInstructions(0, """
                sget-object v0, $defaultPanel
                invoke-static {}, Lcom/google/protobuf/ExtensionRegistryLite;->getGeneratedRegistry()Lcom/google/protobuf/ExtensionRegistryLite;
                move-result-object v1
                invoke-static {v0, p0, v1}, Laxbo;->parseFrom(Laxbo;[BLcom/google/protobuf/ExtensionRegistryLite;)Laxbo;
                move-result-object v0
                return-object v0
            """)
        })
        bridge("parsePanel", """
            invoke-static {p0}, Lbfzu;->patch_parseLocalQueue([B)Ljava/lang/Object;
            move-result-object v0
            return-object v0
        """)

        getter("responseVideo", "Lahwk;", "b", "Ljava/lang/String;")
        val watchEndpoint = field("Lahwk;", "d", "Lazga;")
        val descriptorEndpoint = field("Laopa;", "b", "Lazga;")
        val descriptorVideo = method("Laopa;", "v", emptyList(), "Ljava/lang/String;")
        bridge("descriptorVideo", """
            check-cast p0, Laopa;
            invoke-virtual {p0}, $descriptorVideo
            move-result-object v0
            return-object v0
        """)
        for ((name, owner, reference) in listOf(
            Triple("responseEndpoint", "Lahwk;", watchEndpoint),
            Triple("descriptorEndpoint", "Laopa;", descriptorEndpoint)
        )) bridge(name, """
            check-cast p0, $owner
            iget-object v0, p0, $reference
            if-eqz v0, :empty
            invoke-virtual {v0}, Lazga;->toByteArray()[B
            move-result-object v0
            return-object v0
            :empty
            const/4 v0, 0x0
            return-object v0
        """)
        getter("responsePlaylist", "Lahwk;", "c", "Ljava/lang/String;")
        getter("responsePanel", "Lahwk;", "h", "Lbfzu;")
        setter("setResponsePanel", "Lahwk;", "h", "Lbfzu;")
        getter("responseNavigation", "Lahwk;", "i", "Lahwb;")
        setter("setResponseNavigation", "Lahwk;", "i", "Lahwb;")
        getter("modelPanel", "Lpqh;", "c", "Lbfzu;")
        getter("modelVideo", "Lpqh;", "a", "Ljava/lang/String;")
        getter("modelPlaylist", "Lpqh;", "b", "Ljava/lang/String;")
        setter("setModelPanel", "Lpqh;", "c", "Lbfzu;")
        getter("adapterPanel", "Lpfy;", "A", "Lbfzu;")
        val controllerModel = field("Lpqj;", "o", "Lpqh;")
        val modelPanel = field("Lpqh;", "c", "Lbfzu;")
        bridge("controllerPanel", """
            check-cast p0, Lpqj;
            iget-object v0, p0, $controllerModel
            if-eqz v0, :done
            iget-object v0, v0, $modelPanel
            :done
            return-object v0
        """)
        val collapsedView = field("Lpqj;", "z", "Landroid/view/View;")
        bridge("openPanel", """
            check-cast p0, Lpqj;
            iget-object v0, p0, $collapsedView
            invoke-virtual {v0}, Landroid/view/View;->performClick()Z
            return-void
        """)

        val panelRefresh = method("Lpqj;", "f", listOf("Lahwk;"), "V")
        val navigatorRefresh = method("Lniq;", "p", listOf("Lahwk;"), "V")
        val backend = field("Lniq;", "f", "Laovn;", true)
        val nativeMode = field("Lniq;", "i", "Z", true)
        val queueManager = field("Lniq;", "k", "Laojx;")
        val storage = field("Laojx;", "i", "Laoju;")
        val endpointParser = field("Laojx;", "k", "Lbpv;")
        val queueConfig = field("Laojx;", "h", "Laooh;")
        val playback = field("Lpqj;", "f", "Laobb;")
        val storageConstructor = method("Lnii;", "<init>", listOf("Laobb;", "Lbpv;", "Laooh;"), "V")
        val storageSwitch = method("Laojx;", "h", listOf("Laoju;"), "V")
        val navigableConstructor = method("Laojl;", "<init>", listOf("Laoju;", "Lbpv;", "Laooh;"), "V")
        val populate = method("Laojj;", "t", listOf("Lahwk;"), "V")
        // A new navigator alone still shares the manager's previous backing queue. Install
        // an isolated native playlist store so local rows load even when Premium is disabled.
        bridge("installStorage", """
            check-cast p0, Lniq;
            check-cast p1, Lpqj;
            check-cast p2, Lahwk;
            iget-object v0, p0, $queueManager
            iget-object v1, v0, $storage
            iget-object v2, p1, $playback
            iget-object v3, v0, $endpointParser
            iget-object v4, v0, $queueConfig
            new-instance v5, Lnii;
            invoke-direct {v5, v2, v3, v4}, $storageConstructor
            new-instance v2, Laojl;
            invoke-direct {v2, v5, v3, v4}, $navigableConstructor
            invoke-virtual {v2, p2}, $populate
            invoke-virtual {v0, v5}, $storageSwitch
            return-object v1
        """)
        bridge("restoreStorage", """
            check-cast p0, Lniq;
            check-cast p1, Laoju;
            iget-object v0, p0, $queueManager
            invoke-virtual {v0, p1}, $storageSwitch
            return-void
        """)
        val switchBackend = method("Lniq;", "w", listOf("Laovn;", "Laovn;"), "V")
        mutableClassDefBy("Lniq;").methods.add(ImmutableMethod(
            "Lniq;", "patch_restoreLocalQueueBackend", listOf(ImmutableMethodParameter("Laovn;", null, null)),
            "V", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null, null, MutableMethodImplementation(4)
        ).toMutable().apply {
            addInstructions(0, """
                iget-object v0, p0, $backend
                invoke-direct {p0, v0, p1}, $switchBackend
                instance-of v0, p1, Laokc;
                iput-boolean v0, p0, $nativeMode
                return-void
            """)
        })
        bridge("captureBackend", """
            check-cast p0, Lniq;
            iget-object v0, p0, $backend
            const/4 v1, 0x0
            iput-boolean v1, p0, $nativeMode
            return-object v0
        """)
        bridge("restoreBackend", """
            check-cast p0, Lniq;
            check-cast p1, Laovn;
            invoke-virtual {p0, p1}, Lniq;->patch_restoreLocalQueueBackend(Laovn;)V
            return-void
        """)
        for ((name, native) in listOf("refreshPanel" to panelRefresh, "refreshNavigator" to navigatorRefresh)) {
            bridge(name, """
                check-cast p0, ${native.definingClass}
                check-cast p1, Lahwk;
                invoke-virtual {p0, p1}, $native
                return-void
            """)
        }

        val adapterItems = field("Lpfy;", "d", "Laqod;")
        val eventItems = field("Loci;", "b", "Laqod;")
        bridge("isMoveForAdapter", """
            check-cast p0, Lpfy;
            check-cast p1, Loci;
            iget-object v0, p0, $adapterItems
            iget-object v1, p1, $eventItems
            const/4 v2, 0x0
            if-ne v0, v1, :done
            const/4 v2, 0x1
            :done
            return v2
        """)
        for ((name, member) in listOf("moveFrom" to "c", "moveTo" to "d")) {
            val reference = field("Loci;", member, "I")
            bridge(name, """
                check-cast p0, Loci;
                iget v0, p0, $reference
                return v0
            """)
        }
        val removed = field("Lahmg;", "a", "Ljava/lang/Object;")
        val entryId = field("Lbfzy;", "t", "Ljava/lang/String;")
        bridge("removedEntry", """
            check-cast p0, Lahmg;
            iget-object v0, p0, $removed
            instance-of v1, v0, Lbfzy;
            if-eqz v1, :unknown
            check-cast v0, Lbfzy;
            iget-object v0, v0, $entryId
            return-object v0
            :unknown
            const-string v0, ""
            return-object v0
        """)

        navigatorRefresh.addInstructions(0, "invoke-static/range {p0 .. p1}, $EXTENSION->onResponse(Ljava/lang/Object;Ljava/lang/Object;)V")
        method("Lpqj;", "g", listOf("Laopa;"), "V").addInstructions(0,
            "invoke-static/range {p1 .. p1}, $EXTENSION->onPlaybackDescriptor(Ljava/lang/Object;)V")

        // aojx.h always calls aokg.d(..., true), which stops the stream even if the
        // selected video is unchanged. Block playback dispatch only during our own edits.
        method("Laokg;", "d", listOf("Lisk;", "Laokf;", "Z"), "V").addInstructions(0, """
            invoke-static {}, $EXTENSION->preservePlayback()Z
            move-result v0
            if-eqz v0, :original
            return-void
            :original
            nop
        """)
        val setModel = method("Lpqj;", "q", listOf("Lpqh;"), "V")
        setModel.addInstructions(0, "invoke-static/range {p0 .. p1}, $EXTENSION->onPanelModel(Ljava/lang/Object;Ljava/lang/Object;)V")

        // A local refresh must not be rejected because the preceding playback descriptor was
        // for a different video. The host normally checks this to discard stale responses.
        val identityFields = listOf("C", "D", "E").map { field("Lpqj;", it, "Ljava/lang/String;", true) }
        val refreshBridge = mutableClassDefBy(BRIDGE).methods.single { it.name == "refreshPanel" }
        refreshBridge.addInstructions(1, """
            const/4 v0, 0x0
            ${identityFields.joinToString("\n") { "iput-object v0, p0, $it" }}
        """)

        val context = method("Lpqj;", "r", List(3) { "Ljava/lang/String;" }, "V")
        context.addInstructions(0, """
            invoke-static/range {p0 .. p3}, $EXTENSION->onPlaybackContext(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
            move-result v0
            if-eqz v0, :original
            return-void
            :original
            nop
        """)
        val gestures = method("Lpfy;", "gt", listOf("Ljava/lang/Class;", "Ljava/lang/Object;", "I"), "[Ljava/lang/Class;")
        gestures.addInstructions(0, """
            move-object/from16 v0, p0
            move-object/from16 v1, p2
            move/from16 v2, p3
            invoke-static {v0, v1, v2}, $EXTENSION->onGesture(Ljava/lang/Object;Ljava/lang/Object;I)Z
            move-result v0
            if-eqz v0, :original
            const/4 v0, 0x0
            return-object v0
            :original
            nop
        """)

        // Use the editable compact native row. Other density variants do not implement drag.
        val rowFactory = method("Lpfy;", "v", listOf("Ljava/util/List;"), "Ljava/util/List;")
        val rowStyleField = field("Lpfy;", "B", "I")
        val styleIndex = rowFactory.implementation!!.instructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() == rowStyleField
        }
        check(styleIndex >= 0) { "Native row style selection missing" }
        val styleRegister = rowFactory.getInstruction<TwoRegisterInstruction>(styleIndex).registerA
        rowFactory.addInstructions(styleIndex + 1, """
            invoke-static {v$styleRegister, p0}, $EXTENSION->rowStyle(ILjava/lang/Object;)I
            move-result v$styleRegister
        """)
        val rowBind = method("Loxk;", "gR", listOf("Laqnk;", "Ljava/lang/Object;"), "V")
        val wrapper = field("Loxj;", "a", "Lbfzy;")
        val rowEntry = field("Loxk;", "T", "Ljava/lang/String;")
        val rowView = field("Loxk;", "a", "Landroid/view/View;")
        val localRow = "Loxk;->patch_isLocalQueueRow:Z"
        mutableClassDefBy("Loxk;").fields.add(ImmutableField(
            "Loxk;", "patch_isLocalQueueRow", "Z", AccessFlags.PRIVATE.value,
            null, null, null
        ).toMutable())
        rowBind.addInstructions(0, """
            move-object/from16 v0, p2
            check-cast v0, Loxj;
            iget-object v0, v0, $wrapper
            iget-object v0, v0, $entryId
            invoke-static {v0}, $EXTENSION->isLocalEntry(Ljava/lang/String;)Z
            move-result v0
            move-object/from16 v1, p0
            iput-boolean v0, v1, $localRow
        """)
        val returns = rowBind.implementation!!.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }.reversed()
        for (index in returns) rowBind.addInstructions(index, """
            move-object/from16 v0, p0
            iget-object v1, v0, $rowEntry
            iget-object v0, v0, $rowView
            invoke-static {v0, v1}, $EXTENSION->onRowBound(Landroid/view/View;Ljava/lang/String;)V
        """)
        method("Loxk;", "s", emptyList(), "Z").addInstructions(0, """
            iget-boolean v0, p0, $localRow
            if-eqz v0, :original
            const/4 v0, 0x1
            return v0
            :original
            nop
        """)

        val visibility = method("Lpqj;", "l", emptyList(), "V")
        val flagIndex = visibility.implementation!!.instructions.indexOfFirst {
            val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
            reference?.name == "aX" && reference.returnType == "Z"
        }
        check(flagIndex >= 0) { "Native playlist visibility gate missing" }
        val flagRegister = visibility.getInstruction<OneRegisterInstruction>(flagIndex + 1).registerA
        visibility.addInstructions(flagIndex + 2, """
            invoke-static {v$flagRegister, p0}, $EXTENSION->allowPanel(ZLjava/lang/Object;)Z
            move-result v$flagRegister
        """)
        val switchIndex = navigatorRefresh.implementation!!.instructions.indexOfFirst {
            val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
            reference?.name == "m" && reference.parameterTypes == listOf("J", "Z") && reference.returnType == "Z"
        }
        check(switchIndex >= 0) { "Native queue navigator switch missing" }
        val switchRegister = navigatorRefresh.getInstruction<OneRegisterInstruction>(switchIndex + 1).registerA
        navigatorRefresh.addInstructions(switchIndex + 2, """
            invoke-static {v$switchRegister, p1}, $EXTENSION->allowNavigator(ZLjava/lang/Object;)Z
            move-result v$switchRegister
        """)

        setExtensionIsPatchIncluded(EXTENSION)
    }
}
