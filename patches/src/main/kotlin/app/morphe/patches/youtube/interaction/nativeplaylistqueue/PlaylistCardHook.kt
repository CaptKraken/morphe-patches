/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val CARD_SOURCE = "$EXTENSION_PACKAGE/PlaylistCardSource"

/** Read the mounted card's native tap command without invoking or replacing its listener. */
internal fun installPlaylistCardSource(context: BytecodePatchContext) = with(context) {
    val tag = PlaylistNodeInfoFingerprint.originalMethod.implementation!!.instructions.toList()
    val store = tag.filter { it.opcode == Opcode.IPUT_OBJECT }
        .mapNotNull { it.getReference<FieldReference>() }.single()
    val nodeType = store.type
    check(tag.any { it.opcode == Opcode.CHECK_CAST && it.getReference<TypeReference>()?.type == nodeType }) {
        "Litho NodeInfo must be the object cast and stored by its native view tag"
    }

    val click = PlaylistClickListenerFingerprint.originalMethod
    val code = click.implementation!!.instructions.toList()
    val handler = code.filter { it.opcode == Opcode.IGET_OBJECT }
        .mapNotNull { it.getReference<FieldReference>() }.filter { it.definingClass == click.definingClass }
        .distinctBy { it.toString() }.single()
    val owner = code.filter { it.opcode == Opcode.IGET_OBJECT }
        .mapNotNull { it.getReference<FieldReference>() }.filter { it.definingClass == handler.type }
        .distinctBy { it.toString() }.single()
    val event = code.filter { it.opcode == Opcode.IPUT_OBJECT }
        .mapNotNull { it.getReference<FieldReference>() }
        .distinctBy { it.toString() }.single()
    val nodeClick = playlistClickMountFingerprint(nodeType, click.definingClass, handler.type)
        .instructionMatches.first().instruction.getReference<FieldReference>()!!
    val ownerType = PlaylistTapOwnerFingerprint.originalClassDef.type
    val dispatch = playlistTapActionsFingerprint(ownerType, event, handler.type).match()
    val actions = dispatch.instructionMatches[1].instruction.getReference<FieldReference>()!!
    val callback = dispatch.instructionMatches[2].instruction.getReference<MethodReference>()!!
    val tap = playlistTapCommandFingerprint(
        callback.definingClass,
        callback.parameterTypes.map { it.toString() }
    ).match()
    val decode = tap.instructionMatches.single().instruction.getReference<MethodReference>()!!
    val future = tap.originalMethod.implementation!!.instructions.filter { it.opcode == Opcode.IGET_OBJECT }
        .mapNotNull { it.getReference<FieldReference>() }
        .filter { it.definingClass == tap.originalClassDef.type && it.type == decode.definingClass }
        .distinctBy { it.toString() }.single()

    mutableClassDefBy(nodeType).apply {
        interfaces.add("$CARD_SOURCE\$TapActions;")
        addBridgeMethod(
            "patch_getPlaylistTapActions",
            emptyList(),
            "Ljava/util/List;",
            2,
            """
                iget-object v0, p0, $nodeClick
                if-eqz v0, :empty
                iget-object v0, v0, $owner
                instance-of v1, v0, $ownerType
                if-eqz v1, :empty
                check-cast v0, $ownerType
                iget-object v0, v0, $actions
                return-object v0

                :empty
                const/4 v0, 0x0
                return-object v0
            """
        )
    }
    mutableClassDefBy(future.definingClass).apply {
        interfaces.add("$CARD_SOURCE\$TapCommand;")
        addBridgeMethod(
            "patch_getPlaylistTapCommand",
            emptyList(),
            "Lcom/google/protobuf/MessageLite;",
            1,
            """
                iget-object v0, p0, $future
                invoke-virtual {v0}, $decode
                move-result-object v0
                return-object v0
            """
        )
    }
}
