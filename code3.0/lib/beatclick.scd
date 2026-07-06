// Shared audible-click SynthDef and helper, used by all three plans so a beat
// produces a sound, not just a postln. SynthDef.add queues client-side and is
// sent to the server on next boot; safe to call before s.waitForBoot.
//
// Defines:
//   \beatClick           — short percussive sine, freed via doneAction:2.
//   ~playBeatClick.(D)   — plays one click; pass true for downbeats (higher,
//                           louder) or false for regular beats.

SynthDef(\beatClick, { |amp = 0.9, freq = 880, pan = 0|
    var env = EnvGen.kr(Env.perc(0.000, 0.06), doneAction: 2);
	// var sig = SinOsc.ar(freq) * env * amp;
	var sig = Impulse.ar(1,0,1) * amp;
	sig = Pan2.ar(sig, pan);
    Out.ar(0, sig);
}).add;

~playBeatClick = { |isDown, pan|
    Synth(\beatClick, [
        \freq, if (isDown) { 1320 } { 880 },
        \amp,  if (isDown) { 0.9 }  { 0.5 },
		\pan, pan
    ]);
};




// { Impulse.ar(1,pi/4,1) }.play