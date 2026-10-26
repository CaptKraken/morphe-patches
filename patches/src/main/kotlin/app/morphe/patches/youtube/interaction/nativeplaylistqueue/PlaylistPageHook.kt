/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.nativeplaylistqueue

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.getReference
import app.morphe.patches.youtube.misc.contexthook.NextEndpointParentFingerprint
import app.morphe.patches.youtube.video.series.AccountIdentityFingerprint
import app.morphe.patches.youtube.video.series.IdentityFieldGetterFingerprint
import app.morphe.patches.youtube.video.series.IdentityInterfaceMethodFingerprint
import app.morphe.patches.youtube.video.series.NativeBaseRequestDescriptionFingerprint
import app.morphe.patches.youtube.video.series.NativeRequestIdentityFingerprint
import app.morphe.patches.youtube.video.series.NativeRequestStringSetterFingerprint
import app.morphe.patches.youtube.video.series.SignedOutIdentityFingerprint
import app.morphe.patches.youtube.video.series.SignedOutIncognitoFingerprint
import app.morphe.util.findFieldFromToString
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val PAGES = "$EXTENSION_PACKAGE/PlaylistPages;"
private const val SOURCE = "$EXTENSION_PACKAGE/PlaylistPages\$Source;"
private const val ACCOUNT = "$EXTENSION_PACKAGE/PlaylistPage\$Account;"

/** Fetch authenticated source windows without invoking the native player response handlers. */
internal fun installPlaylistPages(context: BytecodePatchContext) = with(context) {
    val requestType = NextEndpointParentFingerprint.originalClassDef.type
    val serializer = NextEndpointParentFingerprint.originalMethod.name
    fun requestField(label: String) = playlistPageRequestFieldFingerprint(requestType, serializer, label)
        .instructionMatches.last().instruction.getReference<FieldReference>()!!
    val playlist = requestField("playlistId")
    val params = requestField("params")
    val index = playlistPageIndexFingerprint(requestType, serializer)
        .instructionMatches.first().instruction.getReference<FieldReference>()!!
    val paramsSetter = NativeRequestStringSetterFingerprint(requestType, params.name).originalMethod
    val tracking = NativeBaseRequestDescriptionFingerprint.instructionMatches[1]
        .instruction.getReference<FieldReference>()!!
    val factory = playlistPageFactoryFingerprint(requestType).originalMethod
    val entryId = playlistPagePreparationFingerprint(requestType, factory, paramsSetter)
        .instructionMatches[3].instruction.getReference<FieldReference>()!!
    check(entryId != playlist) { "Native playlist entry ID must be distinct from playlist ID" }

    val panelType = PlaylistModelDescriptionFingerprint.originalMethod
        .findFieldFromToString(", playlistPanel=").type
    val blocking = playlistPageServiceFingerprint(requestType, factory.definingClass) { type ->
        classDefByOrNull(type)?.fields?.any { it.type == panelType } == true
    }.originalMethod
    val responseType = blocking.returnType
    val responseConstructor = nativeQueueResponseConstructorFingerprint(responseType).originalMethod
    val rawResponse = responseConstructor.parameterTypes.single().toString()
    val responsePanel = classDefBy(responseType).fields.single { it.type == panelType }
    val navigationType = PlaylistModelDescriptionFingerprint.originalMethod
        .findFieldFromToString(", navigationSets=").type
    val responseNavigation = classDefBy(responseType).fields.single { it.type == navigationType }
    val defaultNavigation = playlistPageDefaultNavigationFingerprint(navigationType).originalMethod
    val navigationSet = classDefBy(defaultNavigation.returnType)
    val navigationConstructor = navigationSet.methods.single { it.name == "<init>" }
    val navigationProto = navigationSet.fields.single { it.type == navigationConstructor.parameterTypes.first() }

    val instructions = blocking.implementation!!.instructions.toList()
    val callback = instructions.first { it.opcode == Opcode.NEW_INSTANCE }.getReference<TypeReference>()!!.type
    val dispatch = instructions.mapNotNull { it.getReference<MethodReference>() }.single {
        it.returnType == "V" && it.parameterTypes.size == 3
    }
    val contextGetter = instructions.mapNotNull { it.getReference<MethodReference>() }.single {
        it.definingClass == blocking.definingClass && it.parameterTypes.isEmpty() &&
                it.returnType == dispatch.parameterTypes[2]
    }
    val callbackConstructor = instructions.mapNotNull { it.getReference<MethodReference>() }
        .first { it.name == "<init>" && it.parameterTypes.isEmpty() }
    val service = mutableClassDefBy(blocking.definingClass)
    val factoryField = service.fields.single { it.type == factory.definingClass }
    val dispatchOwner = instructions.mapNotNull { it.getReference<FieldReference>() }
        .single { it.definingClass == service.type }

    // Reuse the account identity contract also used by Series tracker privacy.
    // Matching that contract does not select or install the Series tracker patch.
    val identity = AccountIdentityFingerprint.originalClassDef
    fun getters(field: FieldReference): List<Method> = IdentityFieldGetterFingerprint(field).matchAll(identity)
        .map { it.originalMethod }.filter { method ->
            identity.interfaces.any { type ->
                classDefByOrNull(type)?.let { IdentityInterfaceMethodFingerprint(method).matchOrNull(it) } != null
            }
        }
    val diagnostic = AccountIdentityFingerprint.originalMethod
    val idGetter = getters(diagnostic.findFieldFromToString("AccountIdentity{getId=")).single()
    val signedOut = SignedOutIdentityFingerprint.originalClassDef
    val incognitoGetter = getters(diagnostic.findFieldFromToString(", isIncognito=")).single {
        SignedOutIncognitoFingerprint(it).matchOrNull(signedOut) != null
    }
    val accountType = identity.interfaces.single { type ->
        classDefByOrNull(type)?.let { IdentityInterfaceMethodFingerprint(idGetter).matchOrNull(it) } != null
    }
    val requestIdentity = NativeRequestIdentityFingerprint(accountType).originalMethod

    service.interfaces.add(SOURCE)
    service.addBridgeMethod(
        "patch_playlistAccount",
        listOf(requestType),
        ACCOUNT,
        4,
        """
            invoke-virtual {p1}, $requestIdentity
            move-result-object v0
            if-eqz v0, :unknown
            invoke-interface {v0}, $accountType->${idGetter.name}()Ljava/lang/String;
            move-result-object v1
            if-eqz v1, :unknown
            invoke-interface {v0}, $accountType->${incognitoGetter.name}()Z
            move-result v2
            new-instance v3, $ACCOUNT
            invoke-direct {v3, v1, v2}, $ACCOUNT-><init>(Ljava/lang/String;Z)V
            return-object v3

            :unknown
            const/4 v0, 0x0
            return-object v0
        """
    )
    service.addBridgeMethod(
        "patch_getPlaylistAccount",
        emptyList(),
        ACCOUNT,
        2,
        """
            iget-object v0, p0, $factoryField
            const/4 v1, 0x0
            invoke-virtual {v0, v1}, $factory
            move-result-object v0
            invoke-virtual {p0, v0}, ${service.type}->patch_playlistAccount($requestType)$ACCOUNT
            move-result-object v0
            return-object v0
        """
    )
    service.addBridgeMethod(
        "patch_requestPlaylistPage",
        listOf("Ljava/lang/String;", "Ljava/lang/String;", "I", "Ljava/lang/String;", "Ljava/lang/String;", "[B", ACCOUNT),
        "Ljava/util/concurrent/Future;",
        4,
        """
            iget-object v0, p0, $factoryField
            invoke-virtual {v0, p2}, $factory
            move-result-object v0
            invoke-virtual {p0, v0}, ${service.type}->patch_playlistAccount($requestType)$ACCOUNT
            move-result-object v1
            if-eqz p7, :unknown
            invoke-virtual {p7, v1}, $ACCOUNT->equals(Ljava/lang/Object;)Z
            move-result v1
            if-eqz v1, :unknown
            iput-object p1, v0, $playlist
            iput p3, v0, $index
            iput-object p5, v0, $entryId
            invoke-virtual {v0, p4}, $paramsSetter
            # Native validation requires non-null tracking even for read-only requests.
            iput-object p6, v0, $tracking
            new-instance v1, $callback
            invoke-direct {v1}, $callbackConstructor
            invoke-virtual {p0}, $contextGetter
            move-result-object v2
            iget-object v3, p0, $dispatchOwner
            invoke-virtual {v3, v0, v1, v2}, $dispatch
            return-object v1

            :unknown
            const/4 v0, 0x0
            return-object v0
        """
    )
    for (navigation in listOf(false, true)) {
        service.addBridgeMethod(
            if (navigation) "patch_getPlaylistNavigation" else "patch_getPlaylistPage",
            listOf("Ljava/lang/Object;"),
            "Lcom/google/protobuf/MessageLite;",
            1,
            """
                check-cast p1, $rawResponse
                new-instance v0, $responseType
                invoke-direct {v0, p1}, $responseConstructor
                ${if (!navigation) "iget-object v0, v0, $responsePanel" else """
                    iget-object v0, v0, $responseNavigation
                    if-eqz v0, :done
                    invoke-virtual {v0}, $defaultNavigation
                    move-result-object v0
                    if-eqz v0, :done
                    iget-object v0, v0, $navigationProto
                """}
                :done
                return-object v0
            """
        )
    }
    service.methods.single { AccessFlags.CONSTRUCTOR.isSet(it.accessFlags) }.apply {
        implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }.reversed().forEach { position ->
                addInstruction(position, "invoke-static/range {p0 .. p0}, $PAGES->attach($SOURCE)V")
            }
    }
}
