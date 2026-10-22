/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.youtube.layout.originaltitles.PlaylistPanelVideoBindFingerprint
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** Observe native row commands without replacing their listener or routing. */
internal fun installPlaylistRowPlayback(
    context: BytecodePatchContext,
    modelField: FieldReference,
    panelField: FieldReference
) = with(context) {
    // Original titles also modifies these binders. Inspect fresh matches, including
    // all native layouts, and require their shared video model and click listener.
    PlaylistPanelVideoBindFingerprint.clearMatch()
    val binders = PlaylistPanelVideoBindFingerprint.matchAll().toList()
    check(binders.isNotEmpty()) { "Native playlist queue: playlist row binders are missing" }
    val videoIdFields = binders.map {
        it.instructionMatches[2].instruction.getReference<FieldReference>()!!
    }.distinct()
    check(videoIdFields.size == 1) {
        "Native playlist queue: row binders disagree on the native video ID field: $videoIdFields"
    }
    val videoIdField = videoIdFields.single()
    val videoType = videoIdField.definingClass
    installPlaylistReordering(context, videoType)
    val listenerTypes = binders.flatMap { binder ->
        binder.originalClassDef.fields.map { it.type }.distinct().filter { type ->
            classDefByOrNull(type)?.interfaces?.contains("Landroid/view/View\$OnClickListener;") == true
        }
    }.distinct().filter { playlistNativeClickFingerprint(it).originalMethodOrNull != null }
    check(listenerTypes.size == 1) {
        "Native playlist queue: expected one native playlist row listener, found ${listenerTypes.size}: $listenerTypes"
    }
    val listenerType = listenerTypes.single()
    val click = classDefBy(listenerType).methods.single {
        it.name == "onClick" && it.parameterTypes == listOf("Landroid/view/View;")
    }
    // The native veto predicate runs before logging decorates the bound endpoint.
    // Observe its original identity after the veto, never the decorated copy.
    val decorate = click.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
        .single { it.parameterTypes.size == 1 && it.returnType == it.parameterTypes.single() &&
                it.returnType.startsWith("L") }
    val listenerEndpoint = classDefBy(listenerType).fields.single { it.type == decorate.returnType }
    val videoEndpoint = classDefBy(videoType).fields.single {
        it.type == decorate.returnType && AccessFlags.PUBLIC.isSet(it.accessFlags)
    }
    val factory = playlistItemFactoryFingerprint(videoType).matchSingle()
    val itemVideo = factory.instructionMatches.single().instruction.getReference<FieldReference>()!!
    val contents = playlistContentsFingerprint(panelField.type, factory.originalMethod).matchSingle()
        .instructionMatches.first().instruction.getReference<FieldReference>()!!
    val controller = mutableClassDefBy(modelField.definingClass)
    controller.methods.add(ImmutableMethod(
        controller.type, "patch_findQueueRow",
        listOf(ImmutableMethodParameter("Ljava/lang/Object;", null, null)), "I",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(8)
    ).toMutable().apply {
        addInstructionsWithLabels(0, """
            iget-object v0, p0, $modelField
            if-eqz v0, :missing
            iget-object v0, v0, $panelField
            if-eqz v0, :missing
            iget-object v0, v0, $contents
            invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
            move-result-object v0
            const/4 v1, 0x0
            :next
            invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
            move-result v2
            if-eqz v2, :missing
            invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
            move-result-object v2
            check-cast v2, ${itemVideo.definingClass}
            iget-object v2, v2, $itemVideo
            if-eqz v2, :skip
            if-eq v2, p1, :found
            iget-object v2, v2, $videoEndpoint
            if-ne v2, p1, :skip
            :found
            return v1
            :skip
            add-int/lit8 v1, v1, 0x1
            goto :next
            :missing
            const/4 v0, -0x1
            return v0
        """)
    })
    installPlaylistNavigation(context, controller.type, videoEndpoint.type)
    // A nonserialized bit belongs to the exact native object, survives a stale
    // view callback, and disappears with that object. No native model is retained
    // in an extension cache, and protobuf equality/serialization stay unchanged.
    val video = mutableClassDefBy(videoType)
    val nativeVideoId = video.fields.single { it.name == videoIdField.name && it.type == videoIdField.type }
    val flyoutVideoInterface =
        "Lapp/morphe/extension/youtube/patches/utils/FlyoutUtils\$FlyoutMenuVideoIdInterface;"
    video.interfaces.add(flyoutVideoInterface)
    video.methods.add(ImmutableMethod(videoType, "patch_getVideoId", emptyList(),
        "Ljava/lang/String;", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(2)).toMutable().apply {
        addInstructionsWithLabels(0, """
            iget-object v0, p0, $nativeVideoId
            return-object v0
        """)
    })
    val ownedField = ImmutableField(videoType, "patch_queueOwned", "Z",
        AccessFlags.PUBLIC.value, null, null, null).toMutable()
    video.fields.add(ownedField)
    video.interfaces.add("Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$QueueRow;")
    video.methods.add(ImmutableMethod(videoType, "patch_isQueueOwned", emptyList(), "Z",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(2)).toMutable().apply {
        addInstructionsWithLabels(0, """
            iget-boolean v0, p0, $ownedField
            return v0
        """)
    })
    // Retain ownership on old header models too, so a stale Clear action is consumed
    // instead of falling through to YouTube's ordinary Hide command.
    val panel = mutableClassDefBy(panelField.type)
    val panelOwned = ImmutableField(panel.type, "patch_queueOwned", "Z",
        AccessFlags.PUBLIC.value, null, null, null).toMutable()
    panel.fields.add(panelOwned)
    panel.interfaces.add("Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$QueuePanel;")
    panel.methods.add(ImmutableMethod(panel.type, "patch_isQueueOwned", emptyList(), "Z",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(2)).toMutable().apply {
        addInstructionsWithLabels(0, """
            iget-boolean v0, p0, $panelOwned
            return v0
        """)
    })
    controller.methods.add(ImmutableMethod(controller.type, "patch_markQueueRows", emptyList(),
        "V", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(5)).toMutable().apply {
        addInstructionsWithLabels(0, """
            iget-object v0, p0, $modelField
            if-eqz v0, :done
            iget-object v0, v0, $panelField
            if-eqz v0, :done
            const/4 v1, 0x1
            iput-boolean v1, v0, $panelOwned
            iget-object v0, v0, $contents
            invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
            move-result-object v0
            const/4 v1, 0x1
            :next
            invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
            move-result v3
            if-eqz v3, :done
            invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
            move-result-object v2
            check-cast v2, ${itemVideo.definingClass}
            iget-object v2, v2, $itemVideo
            if-eqz v2, :next
            iput-boolean v1, v2, $ownedField
            goto :next
            :done
            return-void
        """)
    })
    // Logging can replace the listener's endpoint after a click. Retain the
    // bound identity across a rejected/repeated click, and clear it on unbind.
    val listener = mutableClassDefBy(listenerType)
    val boundEndpoint = ImmutableField(listenerType, "patch_queueEndpoint", "Ljava/lang/Object;",
        AccessFlags.PRIVATE.value, null, null, null).toMutable()
    listener.fields.add(boundEndpoint)
    val clickInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$RowClick;"
    listener.interfaces.add(clickInterface)
    listener.methods.add(ImmutableMethod(listenerType, "patch_getQueueEndpoint", emptyList(),
        "Ljava/lang/Object;", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
        MutableMethodImplementation(2)).toMutable().apply {
        addInstructionsWithLabels(0, """
            iget-object v0, p0, $boundEndpoint
            return-object v0
        """)
    })
    val bindings = playlistEndpointBindingFingerprint(listenerEndpoint).matchAll().toList()
    check(bindings.size == 2) { "Native playlist queue: expected native bind and unbind paths" }
    bindings.forEach { binding ->
        val store = binding.instructionMatches.single()
        val registers = store.instruction as TwoRegisterInstruction
        binding.method.addInstruction(store.index + 1,
            "iput-object v${registers.registerA}, v${registers.registerB}, $boundEndpoint")
    }
    val endpointRead = playlistClickEndpointFingerprint(listenerEndpoint, decorate).matchSingle()
    val listenerRegister = (endpointRead.instructionMatches.first().instruction as TwoRegisterInstruction).registerB
    endpointRead.method.addInstruction(endpointRead.instructionMatches.first().index + 1,
        "invoke-static/range {v$listenerRegister .. v$listenerRegister}, $EXTENSION_CLASS->onRowClicked($clickInterface)V")
    installPlaylistSwipeBackground(context, listenerType)
}
