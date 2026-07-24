// code3.0/cotf/bind-check.scd
//
// AirKit copy of the COTF shared boot-time verification helper
// (source: Concerts-of-the-Future/audio/supercollider/lib/bind-check.scd).
// Sourced by cotf/main_cotf.scd via thisProcess.interpreter.executeFile
// from inside s.waitForBoot. Three responsibilities:
//
//   1. Compare the actually-bound s.options.outDevice against the expected
//      device name. Post a single scannable line either way.
//   2. Verify the device's REAL CoreAudio output channel count against
//      ~expectedMinOutChannels (default 144). The name check alone cannot
//      catch a MOTU-less boot: on 2026-07-24 the venue power-cycled with the
//      MOTU unplugged, the Aggregate still existed under its usual name,
//      scsynth bound it happily at 16 channels (BlackHole alone), this file
//      printed "BOUND ... OK" — and no room audio was possible. The count
//      comes from system_profiler (CoreAudio truth), NOT
//      s.options.numOutputBusChannels, which is only what we REQUESTED.
//   3. If either check fails, set ~ready = false. Under PM2 (PM2_HOME set)
//      a channel-count failure also exits(1) after a short grace so PM2
//      restart-loops slowly until the MOTU is back — a climbing restart
//      counter is a visible red; a name-matched bind is green forever. In
//      the IDE it only refuses (no exit — don't kill a live-coding session).
//
// Convention: the calling patch sets ~expectedDevice and ~patchLabel before
// this file executes — in AirKit that's cotf/config.scd, loaded by
// main_cotf.scd before this file. ~expectedMinOutChannels is optional
// (defaults to 144, the full MOTU 128 + BlackHole 16 Aggregate).
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
    var minChans = ~expectedMinOutChannels ? 144;

    if(actual == expect) {
        // Name matches — now the check the 2026-07-24 incident demanded:
        // does the Aggregate actually CONTAIN its subdevices right now?
        var chans = ("system_profiler SPAudioDataType 2>/dev/null"
            + " | awk '/Aggregate \\(MOTU\\+BlackHole\\):/{f=1} f && /Output Channels:/{print $3; exit}'")
            .unixCmdGetStdOut.asInteger;
        case
        { chans >= minChans } {
            ~ready = true;
            ("[" ++ label ++ "] BOUND: " ++ actual ++ " (" ++ chans ++ " ch) — OK").postln;
        }
        { chans <= 0 } {
            // Parse failure is UNVERIFIED, not broken — don't brick the boot
            // on a system_profiler quirk, but say so loudly and scannably.
            ~ready = true;
            ("[" ++ label ++ "] BOUND: " ++ actual
                ++ " — channel count UNVERIFIED (system_profiler gave no answer)").warn;
        }
        {
            ~ready = false;
            ("[" ++ label ++ "] BOUND: " ++ actual
                ++ " — but only " ++ chans ++ " output channels (expected >= " ++ minChans
                ++ "). MOTU missing from the Aggregate? Plug it in; scsynth cannot rebind live"
                ++ " — REFUSING TO RUN").error;
            if("PM2_HOME".getenv.notNil) {
                "[COTF] exiting in 8s so PM2 retries until the device is back".postln;
                SystemClock.sched(8, { 1.exit });
            };
        };
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
