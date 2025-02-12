{
	Limiter.ar(
		GVerb.ar(
			sin(
				Ringz.ar(
					Impulse.ar(2**LFNoise0.ar(1!4).range(1,4).round ).poll
					,[40,200,234,889,1320,150]*MouseX.kr(0.001,18),
					0.1,3
					).sum).distort
			,1
			,
			2,
			MouseY.kr(0.9,0.2)
		)/2 * 0.3/
	)
}.play


(
SynthDef(\foundSynth, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0, dcy =0.1|

    var env, oscA, oscB, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	oscA = Ringz.ar(K2A.ar(gate), [40,200,234,889,1320,150] * freq.cpsmidi.midiratio, dcy).sum;
	oscB = Ringz.ar(K2A.ar(gate), 1.02 *[40,200,234,889,1320,150] * freq.cpsmidi.midiratio, dcy).sum;
	filt = RLPF.ar([oscA, oscB], filterFreq, fq).tanh;
    sig = filt * env * amp;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig);
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
    \instrument, \foundSynth,
    \note, Pseq([0,3,6,9], inf),
	\octave, Pseq([2,3,4,0], inf),
		\root, Pfunc{(my.getSynchronous.linlin(0,1,0,3).floor * 1)},
	\dur, 0.2,
    \amp, 0.5,
	\attack, Pwhite(0.002,0.03),
    \decay, 0.25,
    \sustain, 0.1,
	\release, 1.1,//Pwhite(0.1,2.4),
		\dcy, Pwhite(0.07,0.1),
		\filterFreq, Pwhite(100,10000) * Pfunc{mx.getSynchronous.linexp(0,1,0.04,1)},
		\fq, Pfunc{mx.getSynchronous.linlin(0,1,0.9,0.1)},
    \pan, Pseq([-0.3, 0.3], inf)
);
).play(quant:0.1);
)