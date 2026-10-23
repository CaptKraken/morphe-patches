/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

/** Resolve the shared native navigation owner for controls and the playback-engine bridge. */
internal fun installPlaylistNavigation(context: BytecodePatchContext, controllerType: String, commandType: String) = with(context) {
    val enumType = NavigationIntentEnumFingerprint.matchSingle().originalClassDef.type
    val wrapperType = playlistNavigationRequestFingerprint(enumType).matchSingle().originalClassDef.type
    val dispatchers = playlistNavigationDispatchFingerprint(wrapperType).matchAll().toList()
    check(dispatchers.size == 3) { "Native playlist queue: expected three navigation dispatchers" }
    fun interfaces(types: Collection<String>): Set<String> {
        val result = mutableSetOf<String>()
        fun visit(type: String) {
            if (result.add(type)) classDefBy(type).interfaces.forEach(::visit)
        }
        types.forEach(::visit)
        return result
    }
    val commonInterfaces = dispatchers.map { interfaces(it.originalClassDef.interfaces) }
        .reduce { first, next -> first intersect next }
    // The native navigator redeclares dispatch inherited from two narrower interfaces.
    // Select their shared descendant, rather than treating each declaration as an owner.
    val navigatorType = commonInterfaces.single { candidate ->
        commonInterfaces.none { other ->
            other != candidate && candidate in interfaces(classDefBy(other).interfaces)
        }
    }
    val provider = playlistNavigatorProviderFingerprint(controllerType, navigatorType).matchSingle()
    val factoryField = provider.instructionMatches[0].instruction.getReference<FieldReference>()!!
    val providerField = provider.instructionMatches[1].instruction.getReference<FieldReference>()!!
    val getProvider = provider.instructionMatches[2].instruction.getReference<MethodReference>()!!
    // Some native factories erase their provider fields to Object; the matched
    // interface invocation and navigator cast still establish the produced type.
    check(factoryField.type == providerField.definingClass &&
            providerField.type in setOf(getProvider.definingClass, "Ljava/lang/Object;")) {
        "Native playlist queue: navigator provider must belong to the active playlist controller"
    }
    val controlsConstructor = provider.originalMethod.implementation!!.instructions
        .mapNotNull { it.getReference<MethodReference>() }
        .single { it.name == "<init>" && it.parameterTypes.firstOrNull() == navigatorType }
    val controlsField = classDefBy(controllerType).fields.single { it.type == controlsConstructor.definingClass }
    val navigatorField = classDefBy(controlsField.type).fields.single { it.type == navigatorType }
    check(AccessFlags.PUBLIC.isSet(navigatorField.accessFlags)) {
        "Native playlist queue: the bound playlist navigator must be readable"
    }
    val controller = mutableClassDefBy(controllerType)
    fun bridge(name: String, returnType: String, body: String) {
        controller.methods.add(ImmutableMethod(controllerType, name, emptyList(), returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(3)).toMutable().apply { addInstructionsWithLabels(0, body) })
    }
    bridge("patch_getNavigator", "Ljava/lang/Object;", """
        iget-object v0, p0, $controlsField
        if-eqz v0, :missing
        iget-object v0, v0, $navigatorField
        return-object v0
        :missing
        const/4 v0, 0x0
        return-object v0
    """)
    installPlaylistNotification(context, wrapperType, enumType, commandType)
    installPlaylistRepeat(context, navigatorType)
    installPlaylistPlayerControls(context, navigatorType, enumType, wrapperType)
}
