(
SynthDef(\miniMoog, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

    var env, osc, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;

	SynthDef(\mouseX, { |bus| Out.kr(bus, MouseX.kr(0,1.0))}).add;
	SynthDef(\mouseY, { |bus| Out.kr(bus, MouseY.kr(0,1.0))}).add;

)

(

	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);

Pdef(\a,
Pbind(
    \instrument, \miniMoog,
    \note, Pseq([0,5,2,9,4], inf),
	\octave, Pseq([2,3].stutter(2) + 2, inf),
	\root, Pfunc{(my.getSynchronous.linlin(0,1,0,3).floor * 4)},
	\dur, 0.2,
    \amp, 1,
	\attack, Pwhite(0.002,0.03),
    \decay, 0.2,
    \sustain, 0.1,
	\release, Pwhite(0.1,2.4),
	\filterFreq, Pwhite(100,10000) * Pfunc{mx.getSynchronous.linexp(0,1,0.04,1)},
	\fq, Pfunc{mx.getSynchronous.linlin(0,1,0.9,0.1)},
    \pan, Pseq([-0.3, 0.3], inf)
);
).play(quant:0.1);
Pdef(\b,
Pbind(
    \instrument, \miniMoog,
    \note, Pseq([0,5,2,9,4], inf),
	\octave, Pseq([2,3].stutter(2) , inf),
	\root, Pfunc{(my.getSynchronous.linlin(0,1,0,3).floor * 4).postln},
	\dur, 1,
    \amp, 1,
	\attack, Pwhite(0.2,0.03),
    \decay, 0.2,
    \sustain, 0.5,
	\release, Pwhite(0.1,2.4),
	\filterFreq, Pwhite(100,10000) * Pfunc{mx.getSynchronous.linexp(0,1,0.04,1)},
	\fq, Pfunc{mx.getSynchronous.linlin(0,1,0.9,0.1)},
    \pan, Pseq([-0.1, 0.1], inf)
);
).play(quant:0.1);

Pdef(\c,
Pbind(
    \instrument, \miniMoog,
    \note, Pseq([0,5,2,9,4], inf),
	\octave, Pseq([2,3].stutter(2) + 4 , inf),
	\root, Pfunc{(my.getSynchronous.linlin(0,1,0,3).floor * 4)},
	\dur, 0.1,
    \amp, 0.6,
	\attack, Pwhite(0.002,0.003),
    \decay, 0.05,
    \sustain, 0.1,
	\release, Pwhite(2.1,5.4),
	\filterFreq, Pwhite(8000,17000) * Pfunc{mx.getSynchronous.linexp(0,1,0.04,1)},
	\fq, Pfunc{mx.getSynchronous.linlin(0,1,0.9,0.01)},
    \pan, Pseq([-0.9, 0.9], inf)
);
).play(quant:0.1);
)



s.meter