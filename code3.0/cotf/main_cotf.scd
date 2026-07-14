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

s.waitForBoot({
    // Device bind check (pattern: COTF audio/supercollider/lib/bind-check.scd).
    thisProcess.interpreter.executeFile(codeRoot +/+ "cotf" +/+ "bind-check.scd");

    // --- per-seat monitor chain: private bus -> gain -> final bus -----------
    // The monitor gain is the real mute (tails ring; the pattern keeps
    // playing so unmute is instant and still beat-synced) and the per-seat
    // volume point. Alive from boot, matching the "process is up even when
    // silent" convention.
    SynthDef(\cotfMonitor, { |in, out, gain = 1, lagT = 0.5|
        Out.ar(out, In.ar(in, 2) * gain.lag(lagT));
    }).add;
    s.sync;

    ~cotfSeatBus = Dictionary();   // seat (1..5) -> private Bus (personalities' ~outBus)
    ~cotfMonitors = Dictionary();  // seat (1..5) -> monitor Synth
    5.do { |i|
        var seat = i + 1;
        var bus = Bus.audio(s, 2);
        ~cotfSeatBus.put(seat, bus);
        ~cotfMonitors.put(seat,
            Synth.tail(s, \cotfMonitor, [\in, bus.index, \out, ~cotfFinalBus.(seat)]));
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

    NetAddr.localAddr.sendMsg("/airkit/startOSCListening", NetAddr.langPort);

    "COTF AirKit up: room %, langPort %, scsynth %, audio %"
        .format(~cotfRoom, NetAddr.langPort, s.addr.port, ~conductorAudioEnabled).postln;
});
)
