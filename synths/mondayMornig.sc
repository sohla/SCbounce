(
SynthDef(\deep80sPad, { |out=0, freq=440, dur=1, amp=0.5, attack=0.01, release=2, pan=0, fq = 1|
    var sound, env, mod, delayTime, chorus, subOsc, sparkle;

    sound = Saw.ar([freq * 0.995, freq * 1.005], amp) * 0.5;
    subOsc = SinOsc.ar(freq * 0.5, 0,amp * 0.8);
    sound = sound + subOsc;
	sound = LPF.ar(sound, freq * fq);
    env = EnvGen.kr(Env.perc(attack, release), doneAction: 2);
    mod = LFTri.kr(0.2, 0).range(0.005, 0.015);
    delayTime = mod + 0.02;
    chorus = DelayN.ar(sound, 0.05, delayTime);
    sparkle = SinOsc.ar(freq * 3.5, 0, 0.05);
    sound = sound + chorus + sparkle;
	sound = Pan2.ar(sound, pan);
    Out.ar(out, sound * env);
}).add;


)
Synth(\deep80sPad, [\freq, 111, \dur, 4, \amp, 0.5]);


(
Pbind(
    \instrument, \deep80sPad,
	\note, Pxrand([0,4,7,12].stutter(4), inf),
	\root, Pseq([0,3,-4,-1].stutter(16),inf),
	\octave, Pxrand([3,4,5].stutter(2), inf),
	\dur, Prand([1,1,1,Rest(1)] * 0.125, inf),
    \amp, Pseq([0.5, 0.4, 0.6, 0.7], inf),
    \attack, Pseq([0.01, 0.1, 0.05, 0.02] * 0.2, inf),
    \release, Pseq([2, 3, 1, 4] * 0.07, inf),
	\pan, Pwhite(-0.35,0.35),
	\fq, Pwhite(1,20)
).play;
)