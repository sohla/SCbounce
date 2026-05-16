// Visual Server Setup Instructions
// The SC classes live in Classes/ and the documentation in HelpSource/.
// Install by copying THE WHOLE directory into the Extensions folder.

(
var src = thisProcess.nowExecutingPath.dirname;
var ext = Platform.userExtensionDir +/+ "visualServer";

"".postln;
"🎨 VISUAL SERVER INSTALLATION 🎨".postln;
"".postln;
"This system is a directory of classes (Classes/) + help (HelpSource/).".postln;
"Copy the WHOLE directory into the Extensions folder.".postln;
"".postln;
"⚠️  REMOVE ANY PREVIOUS INSTALL FIRST".postln;
"   Older versions kept the .sc files flat in Extensions/visualServer.".postln;
"   Leaving them next to the new Classes/ folder causes".postln;
"   DUPLICATE CLASS compile errors. Delete the old one first:".postln;
("   rm -rf " ++ ext.quote).postln;
"".postln;
"📁 INSTALL (copy the whole dir)".postln;
("   cp -r " ++ src.quote ++ " " ++ ext.quote).postln;
"".postln;
"🔗 OR symbolic link (auto-tracks edits in this working copy)".postln;
("   ln -s " ++ src.quote ++ " " ++ ext.quote).postln;
"".postln;
"📋 STRUCTURE".postln;
"   Classes/      - VisualServer, VisualSynthDef, VisualEvent,".postln;
"                   VisualPatterns, VisualRenderer  (auto-compiled)".postln;
"   HelpSource/   - SCDoc help (auto-indexed; see 'Visual Server' guide)".postln;
"   VisualEventTypes.sc  - event types (auto-registered at compile)".postln;
"".postln;
"🔄 AFTER INSTALLATION".postln;
"   1. Recompile class library (Cmd/Ctrl+Shift+L)".postln;
"      → event types (\\visual \\vpulse \\vline \\vlive \\audioVisual)".postln;
"        auto-register; NO manual load needed.".postln;
"   2. Run quickTest.scd to verify".postln;
"".postln;
"📖 DOCUMENTATION".postln;
"   Help browser → search \"Visual Server\" (Guide),".postln;
"   \"Visual Parameters\" (full key reference), or VisualServer.help".postln;
"".postln;
"💡 QUICK START".postln;
"   VisualServer.default.start;".postln;
"   ~v = VisualServer.default.createView(\\demo, Rect(300,200,800,600));".postln;
"   Vbind(\\x, Pwhite(-0.8,0.8), \\size, 60, \\dur, 0.3, \\view, \\demo).vplay;".postln;
"".postln;
)
