# Current checkpoint — PoC49 first queue callback timing test published

Sourcefc92eaa857c17c6fb3e6ed2ce0571e8e66655d3c on feature/youtube-native-playlist-queue; basee5464395441e916b643af4e2175c4e253e6df487. Artifact0cd0032db4a7dc67da5a747ff700dc7df38c9845 on builds/youtube-native-playlist-queue. Both pushes confirmed, both trees clean.
https://raw.githubusercontent.com/CaptKraken/morphe-patches/builds/youtube-native-playlist-queue/native-playlist-queue-21.20.405-poc49-startup-callback-timing.mpp
SHA25646746d61561c5b40b4f5c38a5a13323ff536ac9337e5b25821b9c3c576425605;11906946bytes.

## User result and fix
PoC48 user confirms queuecreated/videoMINIMIZED/browsing preserved, but Logger toast NativePlaylistQueuePatch:Couldnotdisplaynativeplaylistqueue. Exactexception/stacknotprovided; no newlogsrequests, no device reproduction claim. Source realrace: nativepqj.q storesacceptedmodel then ourafter-IPUThookonControllerUpdated->runNowOrLater->updateController(queueNull)->tryStartQueue INLINE, before nativep()/l() subscriptions/render finish. PoC49ONLY deferspendingfirstqueuestart viaUtils.runOnMainThread methodreference so nativecallbackreturns beforepublish. Otherstartupobserversalreadydeferred. Userflow/nativemini flag/Lastguard/genuineerrorreporting untouched.1file3insertions1deletion. No nativehooks/resources/dependencies changes.
Provisional baseline!=nullguard was REMOVED afterreview: ordinaryvideos legitimately lackplaylistmodel; existingnativefactoryfallback mustremain. Initial25sbuildwithguard notpublished. Neverintroduce thatguard withoutverifiednativecontract. ActualsharedUtils inspectedDEX:runOnMainThread->delay0->Handler(mainLooper).postDelayed; alwaysqueued.

## Actual checks
Final :patches:test :patches:buildAndroid passed31s in native-playlist-start-ready-delivery.log.29total24pass5unrelatedJamAPKskips0fail/errors. No newsynthetictest/source-regexcheckfor1dispatchedit. Nativeacceptanceorder/pinnedpostcontract reviewed (start-ready-post-contract.txt). PoC48selected/unselectedSDK/sharedfilterordering evidence reused for UNCHANGEDnativehooks; no freshAPKrun. FinalMPP CRC/currentYouTubeMPE/37Kotlinpayloadsexact/defaulttrue verifiedAFTERdeliveryGradlecompletion. Exactstagedsemanticreview/gatepassed,normalcommit. Evidence native-playlist-start-ready-{integrity,review}.json/staged.diff/cumulative.diff.

## Pending phone
AwaitPoC49: coldstart/homepreviewqueueMINIMIZED/browsepreserved/noerrortoast; clear/restart, ordinaryAdd/NextLastvisibility, Save/New/Undo unchanged. Source-confirmed reentrancybugfixed; exactphoneexceptionnotindependentlyidentified, donotclaimdevicepass. Iferrorpersists needfurthernativeboundaryinvestigationwithoutaskingforlogs or merelyhidingLogger.

## Previous baseline and invariants
PoC48firstqueue/no-player/homepreviewusesnativewatchstart_watch_minimized; Lastonlyqueueexists. QueueStartimmutableintent separateactivequeue, exactacceptedvideo/controller/service guards, cancellation10sidentitybound, noqueuepanelexpansion. SharedwatchcontextAPI injectedFINALIZE aftercached-indexfilters; NEVERreverttoindependententryhook (emittedDEXcaughtfilter-before-descriptorregression despiteSDKpass). Sharedfullscreenexactvideoexceptionboundedexpiry andnextordinarysetting preserved. See start-queue-miniplayer-poc48.md.
PoC47Savepicker/Newwholequeue USERCONFIRMEDWORK, PoC45existingtoastANDUndo USERCONFIRMED. PreserveordinarySave andstockpickerquery(PoC43batchquery400). PoC46FAILEDpickerbinding. Allfeatures21.20.405only/defaulttrue.

## Workflow
Read /workspace/AGENTS.md and WORKFLOW.md. Noagents/delegation/modelrouting. Onefixpergatedcommit/MPP. Source/buildbranchuploads already authorized/noPR. No newlogsrequests. Gate naturallystaleaftercommit; regenerate nextstagedtree. Configdraftsave doesnotPublishsnapshot or verifyrestoration. PriorSTATE-history-before-poc49.md.
