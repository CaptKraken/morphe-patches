# PoC25: modern native Share sheet

User's PoC24 screenshots prove legacy Share UI. Creator/user distinction was wrong.
Legacy navigation90650344 and service387863284 are NOT equivalent UI routes.

Verified stock21.20.405: arqi legacy fragment inflates unified_share_panel_fragment.
Service arpm.b reads bjgz entity1,paneltype2,sheetId3 and calls share/get_share_panel.
Actual anonymous exact row entity requests: type1 returns contents.unifiedSharePanelRenderer;
type2 returns command.elementsCommand.updateActionSheetCommand with requested sheetId,
share_sheet_container model and Share header. type5 returns Android intent endpoint.
Type2 is required, not type1 (previous agent plan guessed type1).

Native blzo ShowActionSheet command278463287 extends ElementsCommand. Fields4 string
sheetId and5 onShow ElementsCommand. apyw.h accepts empty named sheet, creates it,
then executes onShow (lines388-403). ElementsCommand204158123 wrapper and inner
innertube169495254 confirmed captured raw android-next.pb and next_response.proto.
Implementation requests type2 from native onShow, reusing same UUID sheetId. Native
server supplies all contents and targets. Modern service runs original SystemShare
hook when included/enabled; removed our direct legacy override/inclusion marker.
Other row Save/Download/Remove, outside Playnext, background controls unchanged.

Evidence /workspace/scratch/native-playlist-modern-share-{response-check,service}.log;
metadata/share-panel-row-modern.json; share-modern-java/{apyw,blzo,arpm,bjgz}.java;
emitted rowMenu DEX ...modern-share-emitted.txt. No Android device/emulator.
Do not infer modern UI from field decoding/build alone: phone appearance/targets,
nonplaying row ID, dismiss/reopen, SystemShare off/on remain device checks.
