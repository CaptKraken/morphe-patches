/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import app.morphe.util.cloneParameters
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

/** Preserve ordinary native routing; intercept only this controller's local queue navigation. */
internal fun installPlaylistNavigation(context: BytecodePatchContext, controllerType: String) = with(context) {
    val enumType = NavigationIntentEnumFingerprint.matchSingle().originalClassDef.type
    val wrapperType = playlistNavigationRequestFingerprint(enumType).matchSingle().originalClassDef.type
    val intent = classDefBy(wrapperType).fields.single { it.type == enumType }
    val automaticIntent = playlistNavigationIntentFingerprint(enumType, "AUTOPLAY").matchSingle()
        .instructionMatches.last().instruction.getReference<FieldReference>()!!
    val automaticRequest = playlistNavigationSingletonFingerprint(wrapperType, automaticIntent).matchSingle()
        .instructionMatches.last().instruction.getReference<FieldReference>()!!
    val dispatchers = playlistNavigationDispatchFingerprint(wrapperType).matchAll().toList()
    check(dispatchers.size == 3) { "Native playlist queue: expected the three 21.20.405 navigation dispatchers" }
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
    val dispatch = classDefBy(navigatorType).methods.single {
        it.returnType == "V" && it.parameterTypes == listOf(wrapperType)
    }
    val provider = playlistNavigatorProviderFingerprint(controllerType, navigatorType).matchSingle()
    val factoryField = provider.instructionMatches[0].instruction.getReference<FieldReference>()!!
    val providerField = provider.instructionMatches[1].instruction.getReference<FieldReference>()!!
    val getProvider = provider.instructionMatches[2].instruction.getReference<MethodReference>()!!
    check(factoryField.type == providerField.definingClass && providerField.type == getProvider.definingClass) {
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
    bridge("patch_requestAutomaticAdvance", "V", """
        invoke-virtual {p0}, $controllerType->patch_getNavigator()Ljava/lang/Object;
        move-result-object v0
        if-eqz v0, :done
        check-cast v0, $navigatorType
        sget-object v1, $automaticRequest
        invoke-interface {v0, v1}, $dispatch
        :done
        return-void
    """)
    val requestInterface = "Lapp/morphe/extension/youtube/patches/queue/NativePlaylistQueuePatch\$NavigationRequest;"
    mutableClassDefBy(wrapperType).apply {
        interfaces.add(requestInterface)
        methods.add(ImmutableMethod(wrapperType, "patch_getNavigationIntent", emptyList(), "Ljava/lang/Enum;",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            MutableMethodImplementation(2)).toMutable().apply {
            addInstructionsWithLabels(0, """
                iget-object v0, p0, $intent
                return-object v0
            """)
        })
    }
    installPlaylistRepeat(context, controllerType, controlsField, navigatorType)
    installPlaylistPlayerControls(context, navigatorType, enumType, wrapperType)
    dispatchers.forEach { match ->
        for (returnType in listOf("Z", "I")) {
            val capability = mutableClassDefBy(match.originalClassDef.type).methods.single {
                it.returnType == returnType && it.parameterTypes == listOf(wrapperType)
            }
            val method = if (capability.implementation!!.registerCount <= 2) capability.cloneParameters() else capability
            val resultRegister = method.findFreeRegister(0)
            val encodeAvailability = if (returnType == "I") "add-int/lit8 v$resultRegister, v$resultRegister, 0x1" else ""
            method.addInstructionsWithLabels(0, """
                invoke-static/range {p0 .. p1}, $EXTENSION_CLASS->navigationAvailability(Ljava/lang/Object;$requestInterface)I
                move-result v$resultRegister
                if-ltz v$resultRegister, :native_availability
                $encodeAvailability
                return v$resultRegister
                :native_availability
                nop
            """)
        }
        var method = match.method
        if (method.implementation!!.registerCount <= 2) method = method.cloneParameters()
        val freeRegister = method.findFreeRegister(0)
        method.addInstructionsWithLabels(0, """
            invoke-static/range {p0 .. p1}, $EXTENSION_CLASS->onQueueNavigation(Ljava/lang/Object;$requestInterface)Z
            move-result v$freeRegister
            if-eqz v$freeRegister, :native_navigation
            return-void
            :native_navigation
            nop
        """)
    }
}
