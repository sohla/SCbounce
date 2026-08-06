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
// (2026-07-24) Personalities now ship their own state-aware ~onResync
// (concert_p_files.md §6/§15: skip the restart during \tuning where the Pdef
// is deliberately paused, and s.bind { group.freeAll } to kill in-flight
// synths — only the personality can reach its file-scope `group`).
// They install it into topEnvironment; personalityController's
// captureResyncHook moves each install into d.env[\onResyncHook] (per-device,
// so 5 seats can't clobber each other) and restores this dispatcher.
// Personalities without a hook (e.g. cotf_simple*) get the generic restart.
~cotfResyncDispatcher = { |idx|
    // Capture conductor-env values BEFORE entering each device env — inside
    // d.env.use these ~vars resolve against the personality's environment
    // (nil there; first version crashed on nil * nil).
    var clock = ~beatClock;
    var quant = ~scoreBeatsPerBar * ~scoreEventsPerBeat;
    ~devices.do { |d|
        if (d.env.notNil) {
            if (d.env[\onResyncHook].notNil) {
                // Run under topEnvironment: the hook's ~roomState/~beatClock/
                // ~score* live there; its m/group/tuneTime are lexical
                // captures so they survive the environment swap.
                topEnvironment.use { d.env[\onResyncHook].value(idx) };
            } {
                if (d.enabled == true) {
                    d.env.use {
                        if (~model.notNil and: { Pdef(~model.ptn).isPlaying }) {
                            Pdef(~model.ptn).stop;
                            Pdef(~model.ptn).play(clock, quant: quant);
                        };
                    };
                };
            };
        };
    };
    "[COTF] resync at idx %: device patterns re-anchored".format(idx).postln;
};
~onResync = ~cotfResyncDispatcher;

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
                              trimLagT = 0.5, webrtcGain = 1, physicalGain = 0, modeLagT = 0.5,
                              masterGain = 1, masterLagT = 0.5|
        var sig = In.ar(in, 2) * gain.lag(lagT) * trim.lag(trimLagT) * masterGain.lag(masterLagT);
        Out.ar(out, sig * webrtcGain.lag(modeLagT));
        Out.ar(physOut, sig.sum * 0.7071 * physicalGain.lag(modeLagT));
    }).add;
    s.sync;

    // [COTF] Baked-in −10 dB pad on the Room 2 warm-up voices (2026-07-16):
    // they share the MOTU 9/10 Genelec cable and were running hot vs the
    // atmosphere bed. Room 3 (headsets) is unchanged. 10^(-10/20) = 0.3162.
    ~cotfMonitorTrim = if(~cotfRoom == 2, 0.3162, 1.0);

    // [COTF] Per-seat static trim (2026-08-06, Edinburgh day 1): seat 4
    // (percussion) ran hot in the Room 3 chair-speaker mix. Room 3 only —
    // Room 2 keeps its flat room pad. −5 dB = 0.5623. Live-tunable via
    // /airkit/seatTrim (API.md); bake the tuned value HERE so an engine
    // restart re-asserts it with no M0-side persistence.
    ~cotfSeatTrims = if(~cotfRoom == 3, { [1, 1, 1, 0.5623, 1] }, { [1, 1, 1, 1, 1] });

    ~cotfSeatBus = Dictionary();   // seat (1..5) -> private Bus (personalities' ~outBus)
    ~cotfMonitors = Dictionary();  // seat (1..5) -> monitor Synth
    5.do { |i|
        var seat = i + 1;
        var bus = Bus.audio(s, 2);
        var physical = (~cotfRoom == 3) and: { ~cotfOutputMode == "physical" };
        ~cotfSeatBus.put(seat, bus);
        ~cotfMonitors.put(seat,
            Synth.tail(s, \cotfMonitor, [
                \in, bus.index, \out, ~cotfFinalBus.(seat), \trim, ~cotfMonitorTrim * ~cotfSeatTrims[i],
                // Room 2 has no chair speakers: physOut parks on its own final
                // bus with physicalGain pinned 0 (writes nothing audible).
                \physOut, if(~cotfRoom == 3, { ~cotfPhysicalBus.(seat) }, { ~cotfFinalBus.(seat) }),
                \webrtcGain, if(physical, 0, 1),
                \physicalGain, if(physical, 1, 0),
            ]));
    };

    // [COTF] last voiceMute-set gain per seat (1 = unmuted, default). Read by
    // resetSeat's un-duck restore so a reset never un-mutes a seat that a
    // staff voiceMute muted before the reset fired — written additively by
    // \cotfVoiceMute below, never by anything else.
    ~cotfSeatGain = Dictionary.new;

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
            // duck: silence wins over elegance. Fast, fixed lagT — never
            // inherit a sticky \lagT left over from a prior voiceMute call.
            mon !? { mon.set(\lagT, 0.02, \gain, 0) };
            if (d.env.notNil) {
                d.env.use { ~model !? { Pdef(~model.ptn).stop } };
            };
            // Full teardown via the shared unload path (procRout free + ~deinit):
            NetAddr.localAddr.sendMsg("/airkit/unLoadPersonality", d.port);
            // [COTF] d.name is left stale by unLoadPersonality (it only frees
            // procRout + runs ~deinit, it never resets the personality label).
            // Mark it explicitly so M0's getSeats reconcile backstop and the
            // Patches admin page don't keep reporting the torn-down
            // personality as still loaded if the explicit reload is lost.
            // Task 6 (M0-driven reload) should treat "none" the same as any
            // other drifted/silent seat.
            d.name = "none";
            // Restore the duck after the dust settles; the reload (server-driven)
            // arrives with gain audible. Restore to the last voiceMute-set gain
            // (default 1) rather than a literal 1, so a reset never un-mutes a
            // seat that a staff voiceMute muted before the reset fired.
            {
                mon !? { mon.set(\lagT, 0.2, \gain, ~cotfSeatGain.at(seat) ? 1) };
            }.defer(0.6);
            "[COTF] resetSeat %: torn down (port %)".format(seat, d.port).postln;
        };
    };

    OSCdef(\cotfResetSeat, { |msg|
        if (msg.size > 1) {
            var seat = msg[1].asInteger;
            if ((seat >= 1) and: { seat <= 5 }) { ~cotfResetSeat.(seat) } {
                "[COTF] resetSeat: bad seat %".format(seat).postln;
            };
        } {
            "[COTF] resetSeat: bad seat (no arg)".postln;
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
                var g = 1 - muted;
                ~cotfSeatGain.put(d.index, g);   // [COTF] resetSeat's un-duck reads this
                syn.set(\lagT, fade, \gain, g);
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

    // --- [COTF] /airkit/seatTrim <seat 1-5> <linearGain> [fadeSec=0.5] (API.md)
    // Per-seat static trim on top of the room trim — the live tuning knob
    // behind ~cotfSeatTrims' baked defaults. Independent of gain (voiceMute's
    // 0/1) and masterGain (masterLevel); like them it never touches any other
    // seat. Not persisted by M0 — bake the tuned value into ~cotfSeatTrims.
    OSCdef(\cotfSeatTrim, { |msg|
        var seat = msg[1].asInteger.clip(1, 5);
        var level = msg[2].asFloat.clip(0, 2.0);
        var fade = (msg[3] ? 0.5).asFloat.max(0.05);
        ~cotfSeatTrims[seat - 1] = level;
        ~cotfMonitors.at(seat) !? { |syn|
            syn.set(\trimLagT, fade, \trim, ~cotfMonitorTrim * level);
        };
        "[COTF] seatTrim: seat % -> % (fade %s)".format(seat, level.round(0.001), fade).postln;
    }, '/airkit/seatTrim');

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
