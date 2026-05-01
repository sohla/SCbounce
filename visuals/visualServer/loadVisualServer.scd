// Visual Server Setup Instructions
// These are CLASSES that need to be in SuperCollider's class path

(
"".postln;
"🎨 VISUAL SERVER INSTALLATION 🎨".postln;
"".postln;
"This system uses SuperCollider classes that must be installed".postln;
"in the class library path before use.".postln;
"".postln;
"📁 OPTION 1 - Extensions Folder (Recommended)".postln;
("   Extensions Directory: " ++ Platform.userExtensionDir).postln;
"".postln;
"   Method A - Copy entire folder:".postln;
("   cp -r " ++ thisProcess.nowExecutingPath.dirname ++ " " ++ Platform.userExtensionDir +/+ "VisualServer").postln;
"".postln;
"   Method B - Copy just the .sc files:".postln;
("   cp " ++ thisProcess.nowExecutingPath.dirname ++ "/*.sc " ++ Platform.userExtensionDir).postln;
"".postln;
"🔗 OPTION 2 - Symbolic Link".postln;
("   ln -s " ++ thisProcess.nowExecutingPath.dirname ++ " " ++ Platform.userExtensionDir +/+ "VisualServer").postln;
"".postln;
"⚙️  OPTION 3 - sclang_conf.yaml".postln;
"   Add this directory to includePaths in your sclang configuration".postln;
"".postln;
"📋 FILES TO INSTALL (5 classes):".postln;
"   ✓ VisualServer.sc - Core visual server".postln;
"   ✓ VisualSynthDef.sc - Visual instrument definitions".postln;
"   ✓ VisualEvent.sc - Event handling".postln;
"   ✓ VisualPatterns.sc - Pattern classes (Vbind, Vpulse, etc.)".postln;
"   ✓ VisualRenderer.sc - Effects and rendering system".postln;
"".postln;
"🔄 AFTER INSTALLATION:".postln;
"   1. Recompile class library (Cmd/Ctrl+Shift+L)".postln;
"   2. Run quickTest.scd to verify installation".postln;
"".postln;
"💡 QUICK START AFTER INSTALLATION:".postln;
"   // Load event types".postln;
("   (\"" ++ thisProcess.nowExecutingPath.dirname ++ "/VisualEventTypes.scd\").load;").postln;
"   ".postln;
"   // Initialize system".postln;
"   ~v = VisualServer.default;".postln;
"   ~v.start;".postln;
"   ~view = ~v.createView(\\demo, Rect(300, 200, 800, 600));".postln;
"   ".postln;
"   // Create visuals".postln;
"   ~v.vnew(\\circle, nil, \\demo, [\\x, 0, \\y, 0, \\size, 50, \\color, Color.red]);".postln;
"".postln;
"📖 See README.md for complete documentation and examples.".postln;
"🧪 Run quickTest.scd after installation to verify everything works.".postln;
)