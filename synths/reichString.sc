(
	SynthDef(\pluckedString, {
	    |freq=440, amp=0.3, attack=0.001, decay=1, pan=0, filterFreq=2000, filterRes=0.5, phs=10, gate=1|
	    var sig, exciter, delay, env, tone;

		exciter = Impulse.ar(1, SinOsc.ar(phs).range(50,600));
	    delay = freq.reciprocal;
	    sig = CombL.ar(exciter, delay, delay, decay);
	    sig = sig + (SinOsc.ar(freq * 2) * 0.05);
	    sig = sig + (SinOsc.ar(freq * 3) * 0.03);
	    sig = RLPF.ar(sig, filterFreq, filterRes);
	    env = EnvGen.kr(Env.perc(attack, decay), gate, doneAction: 2);
		tone = LFTri.ar([freq,freq * 1.007], 0, 1);
		sig = (sig + tone) * env * amp;
		sig = LeakDC.ar(sig);
	    Out.ar(0, Pan2.ar(sig, pan));
	}).add;

	SynthDef(\mouseX, { |bus| Out.kr(bus, MouseX.kr(0,1.0))}).add;
	SynthDef(\mouseY, { |bus| Out.kr(bus, MouseY.kr(0,1.0))}).add;

)

(

	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);

	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);


~reichPattern = Pbind(
    \instrument, \pluckedString,
    \dur, Pfunc{ mx.getSynchronous.linlin(0.0,1.0,0.35,0.13)},
    \degree, Pseq([
        Pseq([0, 2, 4, 5, 7, 9, 11, 12], 2),
        Pseq([12, 11, 9, 7, 5, 4, 2, 0], 2)
	], inf),
    \scale, Scale.major,
	\root, Pseq([0,1,3,-2,1,3,-2,1].stutter(16), inf),
    \octave, Pseq([3, 4], inf),
	\amp, Pexprand(0.1, 0.2),
	\attack, Pseg(Pseq([0.001, 0.1], inf), Pseq([3, 3], inf), \linear, inf),
    \decay, Pfunc{ mx.getSynchronous.linlin(0.0,1.0,1.5,0.2)},
	\phs, Pseg(Pseq([5, 20], inf), Pseq([10, 10], inf), \sine, inf),
	\filterFreq, Pfunc{ my.getSynchronous.linlin(0.0,1.0,100,10000)},//Pseg(Pseq([1000, 4000], inf), Pseq([30, 30], inf), \sine, inf),
	\filterRes, Pseg(Pseq([0.3, 0.7], inf), Pseq([20, 20], inf), \sine, inf),
    \pan, Pwhite(-0.7, 0.7)
).play;
)
