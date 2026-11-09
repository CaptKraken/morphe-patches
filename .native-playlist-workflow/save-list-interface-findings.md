# Native list Save contracts (after PoC40 restoration)

No new batch implementation. Confirmed21.20.405 native interfaces:
1. ailp request playlist/get_add_to_playlist: List a -> bcnw.d repeated videoIDs; invariant videoIds XOR playlistId. Native lpp.ij constructs it from scalar axvo.c through pbv.b, adding exactly one string. Native legacy selection UI; no verified connection to modern get_panel contents.
2. ailv browse/edit_playlist: bfyp60666189.actions2 repeated bfyo (addedVideoId2/action6); bcni actual request repeated-message field2.
3. ailn playlist/create: List f -> bcns repeated-string field3; mutually exclusive sourcePlaylistId; native jtu dialog SelectedVideoIds accepts list.
4. Modern get_panel PAadd_to_playlist params bcca111->bbbf fields1/2 two scalar strings only; local helper uses111.1videoId, no verified list here. Failed PoC38 CSV is not a native batch contract.

No authenticated request captured/device here, unauth probes401 do not validate/disprove batch. Native built-in list capability established; Premium backend restriction not established. Need exact modern batch/source selection path before modifying it; do not claim wholequeueSave fixed. Keep PoC40 first-ID path and native ordinarySave restored.

False leads eliminated: bieo68997349/iyz/aint is subscription/subscribe, NOT playlistSave; bhdv75581109 SMS; axmz194259413 account-linking capabilities, NOT videoIDs. hsk is shared async error callback (14/15 SaveToPlaylistListEntityUpdateCommandResolver). ibi.b DEX identifies that handler as bgyw with scalar listentitykey/playlistID/op; it updates membership entities, not videoSelection. Decompiled ibi partial/exit1; trust DEX. Do not treat repeatedStrings in unrelated endpoint as videoIDs.

Evidence save-modern-java/{ailp,lpp,ailv,ailn,jtu,aint,hsk,ibi}.java; native-playlist-list-request-factories.txt/-save-selection-handler-dex.txt/-save-selection-command-schema.txt and prior request schema snapshots. New decompiler uses XDG dirs under workspace. No tracked source modified by research.

Current decision: stop unverified batch workaround; exact modern binding unresolved. Complete separately user-authorized Play last in queue after this research; keep regressionfix and addition in separate commits/MPPs.

Follow-up after PoC41: native ailh get_panel request carries params,panelId,continuation,query and separate optional formData bbbg. Six formData extension registrations found:1010bbdj(repeatedbhqe),1065beni(repeatedbemq),1025bfav(bflu+scalar),1060bhcy(bhcx),1070bhrb(bhra),1058bilu(composition). No direct repeatedstring extension; nested types require inspection, no list binding claimed. Evidence save-form-all-extensions.txt,save-form-selected-jadx.log and save-modern-java/ailh.java/sixformclasses. Modern first-target111 fields and separate formData must not be conflated.

FINAL CHECKPOINT: PoC40nativeSave restoration and PoC41Playlast both source/build pushes confirmed. Clean sourceHEADf08ec6d1c and buildsHEAD012f706. NewtestMPP PoC41 includes PoC40fix.23tests/5unrelatedskips/strings/build/defaulttrue/integrity/gatepassed. Phone checks pending, especially player'sSave andendappend order/time. WholequeueSave NOT implemented; modern native selection mapping unresolved after read-only list/formData research. No agents/no logs requested.

Further formDatainspection: bbbg6extensions are typed forms (bbdj repeatedbhqe fields string+Any+enum+typeddata,bool,string; beni repeatedbemq oneofbgrp/bgrq; bfav singlebflu scalar; bhcy Any fields; bhrb composition; bilu composition). No verified playlistvideoIDlist. juo/abxd decompilation incomplete, cannot use missing Java body as proof. Exact native listmutationcapabilities verified, authenticated backendacceptance untested. Failed guessstrategy not restored. Allresearch/scope/review artifacts saved outside source. No pending commands/sessions.
