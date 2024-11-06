// Synth definition for Melbourne crossing sounds
(
SynthDef(\melbCrossing, { |freq=1000, amp=0.3, pan=0, att=0.001, rel=0.1, gate=1, len=0|
    var sig, env, slide;
    env = EnvGen.kr(Env.perc(att, rel), gate, doneAction: 2);
	slide = XLine.kr(freq*2, freq, len);
    sig = SinOsc.ar(slide).cubed * 0.3;
    sig = sig + HPF.ar(BrownNoise.ar(0.01), 1000);  // Add some noise for realism
    sig = sig * env * amp;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig);
}).add;
)
// Pattern definitions for wait and walk signals
(
Pbindef(\waitSignal,
    \instrument, \melbCrossing,
    \freq, 1000,  // "Wait" signal frequency
    \dur, 1.5,    // Slow repeating tick
    \att, 0.001,
    \rel, 0.1,
    \amp, 0.9,
	\len, 0.0
).play;
)

(
var waitCount = 10, walkCount = 40;

Pbindef(\walkSignal,
    \instrument, \melbCrossing,
    \freq, Pseq([Pseq([1000],waitCount), 1000, Pseq([500],walkCount), 1000], inf),
	\dur, Pseq([Pseq([1],waitCount), 0.25, Pseq([0.1],walkCount), Rest(1)], inf),
	\len, Pseq([Pseq([0],waitCount), 0.05, Pseq([0.0],walkCount), 0], inf),
    \att, 0.001,
    \rel, 0.05,
    \amp, 0.9
).play;
)
