// COTF production entry point — boots the SAME controllers as the composer's
// main.sc, with show configuration. No GUI (bench GUI = run main.sc instead).
// Run: /Applications/SuperCollider.app/Contents/MacOS/sclang \
//        /Users/m1/AirKit/code3.0/cotf/main_cotf.scd
// Env: COTF_ROOM=2|3 (default 3), COTF_CONDUCTOR_AUDIO=0|1 (default 1).
(
var codeRoot = thisProcess.nowExecutingPath.dirname.dirname; // .../code3.0

thisProcess.interpreter.executeFile(codeRoot +/+ "cotf" +/+ "config.scd");

// Same load order as main.sc: personality -> osc -> conductor.
thisProcess.interpreter.executeFile(codeRoot +/+ "personalityController.scd");
thisProcess.interpreter.executeFile(codeRoot +/+ "oscController.scd");
thisProcess.interpreter.executeFile(codeRoot +/+ "conductorController.scd");

// [COTF] beat-clock anchor fix (2026-07-15 bench). ~beatClock is a boot-long
// singleton; every seek re-anchors ~beatClock.beats — possibly far BACKWARD
// (seek 0 sets beats=0 after the clock has free-run since boot). A Pdef
// already playing has its next event scheduled in OLD clock beats, which a
// backward jump strands hours in the future: the instrument goes silent while
// the walker, timer and everything else run happily (observed live: harp
// muted at the first QLab seek, "FAILURE /n_set Node not found" red herring).
// Fix: on every (re)anchor the conductor fires ~onResync — restart each live
// device's pattern against the new clock time; the bar quant re-enters in
// musical phase within one bar. COTF-profile only; composer's main.sc is
// untouched (their ~onResync stays the default no-op).
~onResync = { |idx|
    // Capture conductor-env values BEFORE entering each device env — inside
    // d.env.use these ~vars resolve against the personality's environment
    // (nil there; first version crashed on nil * nil).
    var clock = ~beatClock;
    var quant = ~scoreBeatsPerBar * ~scoreEventsPerBeat;
    ~devices.do { |d|
        if (d.env.notNil and: { d.enabled == true }) {
            d.env.use {
                if (~model.notNil and: { Pdef(~model.ptn).isPlaying }) {
                    Pdef(~model.ptn).stop;
                    Pdef(~model.ptn).play(clock, quant: quant);
                };
            };
        };
    };
    "[COTF] resync at idx %: live device patterns re-anchored".format(idx).postln;
};

s.waitForBoot({
    // Device bind check (pattern: COTF audio/supercollider/lib/bind-check.scd).
    thisProcess.interpreter.executeFile(codeRoot +/+ "cotf" +/+ "bind-check.scd");

    // --- per-seat monitor chain: private bus -> gain -> final bus -----------
    // The monitor gain is the real mute (tails ring; the pattern keeps
    // playing so unmute is instant and still beat-synced) and the per-seat
    // volume point. Alive from boot, matching the "process is up even when
    // silent" convention.
    // `trim` is a fixed, per-room output pad — distinct from `gain`, which the
    // per-seat voiceMute owns (0/1). voiceMute never touches trim, so the pad
    // survives every mute/unmute.
    // Dual-destination monitor (2026-07-17 chair speakers): stereo to the
    // BlackHole webrtc bus (as ever) behind webrtcGain, mono sum to the seat's
    // MOTU chair out behind physicalGain. Exactly one gain is 1 at a time
    // (/airkit/outputMode crossfades); the inactive side is HARD 0 because the
    // chair amps are powered in both modes. gain/trim semantics unchanged.
    SynthDef(\cotfMonitor, { |in, out, physOut = 0, gain = 1, lagT = 0.5, trim = 1,
                              webrtcGain = 1, physicalGain = 0, modeLagT = 0.5|
        var sig = In.ar(in, 2) * gain.lag(lagT) * trim;
        Out.ar(out, sig * webrtcGain.lag(modeLagT));
        Out.ar(physOut, sig.sum * 0.7071 * physicalGain.lag(modeLagT));
    }).add;
    s.sync;

    // [COTF] Baked-in −10 dB pad on the Room 2 warm-up voices (2026-07-16):
    // they share the MOTU 9/10 Genelec cable and were running hot vs the
    // atmosphere bed. Room 3 (headsets) is unchanged. 10^(-10/20) = 0.3162.
    ~cotfMonitorTrim = if(~cotfRoom == 2, 0.3162, 1.0);

    ~cotfSeatBus = Dictionary();   // seat (1..5) -> private Bus (personalities' ~outBus)
    ~cotfMonitors = Dictionary();  // seat (1..5) -> monitor Synth
    5.do { |i|
        var seat = i + 1;
        var bus = Bus.audio(s, 2);
        var physical = (~cotfRoom == 3) and: { ~cotfOutputMode == "physical" };
        ~cotfSeatBus.put(seat, bus);
        ~cotfMonitors.put(seat,
            Synth.tail(s, \cotfMonitor, [
                \in, bus.index, \out, ~cotfFinalBus.(seat), \trim, ~cotfMonitorTrim,
                // Room 2 has no chair speakers: physOut parks on its own final
                // bus with physicalGain pinned 0 (writes nothing audible).
                \physOut, if(~cotfRoom == 3, { ~cotfPhysicalBus.(seat) }, { ~cotfFinalBus.(seat) }),
                \webrtcGain, if(physical, 0, 1),
                \physicalGain, if(physical, 1, 0),
            ]));
    };

    // [COTF] latency bench tap: evaluate in the IDE to hear seat 1 on the
    // Genelec (MOTU out 1) AND the headset simultaneously — one phone
    // recording then measures the headset leg (scripts/onset-gap.py, COTF repo):
    //   ~benchTap = Synth.tail(s, \cotfMonitor, [\in, ~cotfSeatBus.at(1).index, \out, 0]);
    //   ~benchTap.free;

    // --- [COTF] /airkit/voiceMute <devicePort> <0|1> [fadeSec] (API.md) -----
    OSCdef(\cotfVoiceMute, { |msg|
        var d = ~devices.at(msg[1].asInteger);
        var muted = msg[2].asInteger.clip(0, 1);
        var fade = (msg[3] ? 1).asFloat.max(0.01);
        d !? {
            ~cotfMonitors.at(d.index) !? { |syn|
                syn.set(\lagT, fade, \gain, 1 - muted);
                "voiceMute: device % (seat %) -> %".format(d.port, d.index, muted).postln;
            };
        };
    }, '/airkit/voiceMute');

    // --- [COTF] /airkit/outputMode <"physical"|"webrtc"> [fadeSec=0.5] (API.md)
    // Crossfades ALL Room 3 seats between chair-speaker and webrtc outs.
    // Room 2 instances ignore it. Additive — never repurposes an address.
    OSCdef(\cotfOutputMode, { |msg|
        var mode = msg[1].asString;
        var fade = (msg[2] ? 0.5).asFloat.max(0.05);
        // includesEqual, NOT includes (identity trap — see config.scd note).
        if ((~cotfRoom == 3) and: { ["physical", "webrtc"].includesEqual(mode) }) {
            ~cotfOutputMode = mode;
            ~cotfMonitors.do { |syn|
                syn.set(\modeLagT, fade,
                    \physicalGain, if(mode == "physical", 1, 0),
                    \webrtcGain, if(mode == "webrtc", 1, 0));
            };
            "[COTF] outputMode -> % (fade %s)".format(mode, fade).postln;
        };
    }, '/airkit/outputMode');

    NetAddr.localAddr.sendMsg("/airkit/startOSCListening", NetAddr.langPort);

    "COTF AirKit up: room %, langPort %, scsynth %, audio %, outputMode %"
        .format(~cotfRoom, NetAddr.langPort, s.addr.port, ~conductorAudioEnabled, ~cotfOutputMode).postln;
});
)
