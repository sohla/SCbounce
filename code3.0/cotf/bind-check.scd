// code3.0/cotf/bind-check.scd
//
// AirKit copy of the COTF shared boot-time verification helper
// (source: Concerts-of-the-Future/audio/supercollider/lib/bind-check.scd).
// Sourced by cotf/main_cotf.scd via thisProcess.interpreter.executeFile
// from inside s.waitForBoot. Two responsibilities:
//
//   1. Compare the actually-bound s.options.outDevice against the expected
//      device name. Post a single scannable line either way.
//   2. If the device is wrong, set ~ready = false. The caller does not
//      silently sound through the wrong speakers.
//
// Convention: the calling patch sets ~expectedDevice and ~patchLabel before
// this file executes — in AirKit that's cotf/config.scd, loaded by
// main_cotf.scd before this file. Example:
//
//   ~expectedDevice = "Aggregate (MOTU+BlackHole)";
//   ~patchLabel     = "Room3/Lumivox";
//
// After this file executes, ~ready is true (bind OK) or false (bind
// WRONG). The COTF original left ~bindCheck as a function the caller
// invokes explicitly; this copy calls it once at the end of the file
// (deviation, noted in task-7-report.md) so a plain executeFile is
// enough — main_cotf.scd's executeFile call needed no changes.

~ready = false;

~bindCheck = {
    var actual  = s.options.outDevice ? "<nil>";
    var expect  = ~expectedDevice ? "<unset>";
    var label   = ~patchLabel ? "<unlabelled>";

    if(actual == expect) {
        ~ready = true;
        ("[" ++ label ++ "] BOUND: " ++ actual ++ " — OK").postln;
    } {
        ~ready = false;
        ("[" ++ label ++ "] BOUND: " ++ actual
            ++ " — EXPECTED " ++ expect
            ++ " — REFUSING TO RUN").warn;
    };
    ~ready
};

// AirKit deviation from the COTF original: run the check immediately on
// execute, since main_cotf.scd only does
// thisProcess.interpreter.executeFile(.../"bind-check.scd") and never
// calls ~bindCheck.() itself.
~bindCheck.();
