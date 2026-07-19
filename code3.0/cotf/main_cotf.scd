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
                              webrtcGain = 1, physicalGain = 0, modeLagT = 0.5,
                              masterGain = 1, masterLagT = 0.5|
        var sig = In.ar(in, 2) * gain.lag(lagT) * trim * masterGain.lag(masterLagT);
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

    // --- COTF per-seat output meter (day-1 fixes, 2026-07-19) ---------------
    // Taps ~cotfSeatBus (pre gain/trim/master) so a masterLevel dip can't fake
    // five dead seats. Peak-hold between /airkit/getLevels polls so sparse
    // plucks register at ~1 Hz polling. Cost: 5 synths, SendReply at 10 Hz.
    SynthDef(\cotfMeter, { |in = 0, seat = 1|
        var sig = In.ar(in, 2);
        var amp = Amplitude.kr(Mix(sig) * 0.5, attackTime: 0.001, releaseTime: 0.1);
        SendReply.kr(Impulse.kr(10), '/cotf/meter', amp, replyID: seat);
    }).add;
    s.sync;

    ~cotfPeaks = Array.fill(5, 0.0);
    ~cotfMeters = Dictionary();
    (1..5).do { |seat|
        ~cotfMeters.put(seat,
            Synth.tail(s, \cotfMeter, [\in, ~cotfSeatBus.at(seat).index, \seat, seat]));
    };

    OSCdef(\cotfMeterAccum, { |msg|
        var seat = msg[2].asInteger, amp = msg[3];
        if ((seat >= 1) and: { seat <= 5 }) {
            ~cotfPeaks[seat - 1] = max(~cotfPeaks[seat - 1], amp);
        };
    }, '/cotf/meter', s.addr);

    OSCdef(\cotfGetLevels, { |msg, time, addr|
        var peaks = ~cotfPeaks.copy;
        ~cotfPeaks = Array.fill(5, 0.0);
        addr.sendMsg("/airkit/levels/reply", ~cotfRoom, *(peaks.collect(_.asFloat)));
    }, '/airkit/getLevels');

    // --- COTF seat-scoped reset (day-1 fixes) -------------------------------
    // Teardown + silence ONLY. The M0 server re-pushes the saved personality
    // (~300ms later) through the ordinary /airkit/loadPersonality path, so a
    // reloaded seat rejoins exactly like any mid-piece personality load — the
    // conductor, ~beatClock and score position are NEVER touched here.
    ~cotfResetSeat = { |seat|
        var d = ~devices.detect { |dev| dev.index == seat };
        var mon = ~cotfMonitors.at(seat);
        if (d.isNil) {
            "[COTF] resetSeat %: no live device — no-op".format(seat).postln;
        } {
            mon !? { mon.set(\gain, 0) };            // duck: silence wins over elegance
            if (d.env.notNil) {
                d.env.use { ~model !? { Pdef(~model.ptn).stop } };
            };
            // Full teardown via the shared unload path (procRout free + ~deinit):
            NetAddr.localAddr.sendMsg("/airkit/unLoadPersonality", d.port);
            // Restore the duck after the dust settles; the reload (server-driven)
            // arrives with gain audible. 1 = unmuted; a server-side voiceMute, if
            // any, is re-asserted by M0's monitor on its next tick.
            { mon !? { mon.set(\gain, 1) } }.defer(0.6);
            "[COTF] resetSeat %: torn down (port %)".format(seat, d.port).postln;
        };
    };

    OSCdef(\cotfResetSeat, { |msg|
        var seat = msg[1].asInteger;
        if ((seat >= 1) and: { seat <= 5 }) { ~cotfResetSeat.(seat) } {
            "[COTF] resetSeat: bad seat %".format(seat).postln;
        };
    }, '/airkit/resetSeat');

    OSCdef(\cotfPanic, {
        "[COTF] panic: resetting all seats".postln;
        (1..5).do { |seat| ~cotfResetSeat.(seat) };
    }, '/airkit/panic');

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

    // --- [COTF] /airkit/masterLevel <linearGain> [fadeSec=0.5] (API.md) ------
    // Per-room overall output level, applied to every seat monitor. Additive:
    // independent of gain (voiceMute's 0/1) and trim (the boot-fixed room pad).
    // M0 owns persistence (settings) and re-asserts on our dead->alive.
    OSCdef(\cotfMasterLevel, { |msg|
        var level = msg[1].asFloat.clip(0, 4.0);
        var fade = (msg[2] ? 0.5).asFloat.max(0.05);
        ~cotfMasterLevel = level;
        ~cotfMonitors.do { |syn| syn.set(\masterLagT, fade, \masterGain, level) };
        "[COTF] masterLevel -> % (fade %s)".format(level.round(0.001), fade).postln;
    }, '/airkit/masterLevel');

    // --- [COTF] /airkit/testTone <seat 1-5> [durSec=1.0] (API.md) -----------
    // Speaker-test burst through the seat's monitor chain (so it proves
    // masterGain + outputMode + the physical/webrtc leg end to end).
    OSCdef(\cotfTestTone, { |msg|
        var seat = msg[1].asInteger.clip(1, 5);
        var dur = (msg[2] ? 1.0).asFloat.clip(0.1, 5.0);
        ~cotfSeatBus.at(seat) !? { |bus|
            { SinOsc.ar(440 * seat.midiratio.max(1), 0, 0.25)
                * EnvGen.kr(Env.linen(0.02, dur - 0.1, 0.08), doneAction: 2) ! 2 }
                .play(s, bus.index, addAction: \addToHead);
            "[COTF] testTone seat % (%s)".format(seat, dur).postln;
        };
    }, '/airkit/testTone');

    NetAddr.localAddr.sendMsg("/airkit/startOSCListening", NetAddr.langPort);

    "COTF AirKit up: room %, langPort %, scsynth %, audio %, outputMode %"
        .format(~cotfRoom, NetAddr.langPort, s.addr.port, ~conductorAudioEnabled, ~cotfOutputMode).postln;
});
)
