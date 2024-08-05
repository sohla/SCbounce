(
SynthDef(\monoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, timeScale: 2,doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;
	SynthDef(\mouseX, { |bus| Out.kr(bus, MouseX.kr(0,1.0))}).add;
	SynthDef(\mouseY, { |bus| Out.kr(bus, MouseY.kr(0,1.0))}).add;

)

(
~sampleFolder = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 25June2024/converted");
~buffers = ~sampleFolder.entries.collect({ |path|
    Buffer.read(s, path.fullPath);
});
)


(
Pdef(\a,
	Pbind(
		\instrument, \monoSampler,
		\bufnum, ~buffers[7],
		\amp,2,
		\octave, Pxrand([3,4,5], inf),
		\rate,1,
		\start, Pseg(Pseq([0,1],inf), 3, \linear),
		\note, Pxrand([34], inf),
		\release,0.1,
		\dur, Pseq([0.7,Rest(0.3)] * 0.15, inf)
	)
);

Pdef(\a).play(quant:[0.15]);
// with versatilePerc
)




(
Pdef(\a,
	Pbind(
		\instrument, \monoSampler,
		\bufnum, ~buffers[21],
		\amp,5,
		\octave, Pxrand([3], inf),
		\rate,1,
		\start, Pseg(Pseq([0.3,0.85],inf), Pseq([3,0], inf), \linear, inf),
		\note, Pseq([34,32].stutter(60*2), inf),
		\attack, 0.07,
		\release,0.2,
		\dur, Pseq([0.7,Rest(0.3)] * 0.1, inf)
	)
);

Pdef(\a).play(quant:[0.15]);
)




(
~sampleFolder = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 25June2024/converted");
// ~sampleFolder = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 2July2025/converted");
~buffers = ~sampleFolder.entries.collect({ |path, i|
	[i,path].postln;
    Buffer.read(s, path.fullPath);
});
)


(
Pdef(\a,
	Pbind(
		\instrument, \stereoSampler,
		\bufnum, ~buffers[9],
		\amp,4,
		\octave, Pxrand([3], inf),
		\rate, Pwhite(0.8,1.15),
		\start, Pseq([0.5,0.645], inf),
		\note, Pseq([33], inf),
		\attack, 0.07,
		\release,0.2,
		\dur, Pseq([0.125], inf)
	)
);

Pdef(\a).play(quant:[0.15]);
)




(
Pdef(\a,
	Pbind(
		\instrument, \stereoSampler,
		\bufnum, ~buffers[2],
		\amp,4,
		\octave, Pxrand([3], inf),
		\rate, Pwhite(1),
		\start, Pxrand([0.1,0.17,0.25,0.32,0.62,0.78,0.81], inf),
		\note, Pseq([33], inf),
		\attack, 0.07,
		\release,0.2,
		\dur, Pseq([0.25], inf)
	)
);

Pdef(\a).play(quant:[0.15]);
)




(
Pdef(\a,
	Pbind(
		\instrument, \stereoSampler,
		\bufnum, ~buffers[3],
		\amp,1,
		\octave, Pxrand([3], inf),
		\rate, Pwhite(1),
		// \start, Pseg(Pseq([0,1], inf), Pseq([6,0],inf), \linear, inf).trace,
		\start, Pseq([17,55,101,134,165,177,198]/220, inf),
		\note, Pseq([33], inf),
		\attack, 0.07,
		\release,0.2,
		\dur, Pseq([0.125] , inf)
	)
);

Pdef(\a).play(quant:[0.15]);
)







//disney fun
(
	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);

Pdef(\a,
	Pbind(
		\instrument, \monoSampler,
		\bufnum, ~buffers[12],
		\amp,1,
		\octave, Pxrand([3], inf),
		\rate, 1,
		\start, Pfunc{ mx.getSynchronous.linlin(0.0,1.0,0,0.9) * (1 + 0.02.rand)},
		\note, Pseq([33,37,33-5,33-12], inf),
		\attack, 0.07,
		\sustain,0.4,
		\release,0.3,
		\dur, Pseq([0.125] , inf)
	)
);

Pdef(\a).play(quant:[0.15]);
)



(//holy mozart
	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);

Pdef(\a,
	Pbind(
		\instrument, \monoSampler,
		\bufnum, ~buffers[29],
		\amp,1,
		\octave, Pxrand([3], inf),
		\rate, 1,
		\root, 0,
		// \start, Pseg(Pseq([0.6,0.9], inf), Pseq([1.5,0],inf), \linear, inf),
		// \start, Pfunc{ mx.getSynchronous.linlin(0.0,1.0,0.55,0.9)},
		\start, Pseq([0.55,0.66], inf),
		\note, Pseq([33,31].stutter(4), inf),
		\attack, 0.07,
		\sustain, 0.5,
		\release,0.35,
		\dur, Pseq([1] , inf)
	)
);

Pdef(\a).play(quant:[0.15]);
)


(//matt cello
	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);

Pdef(\a,
	Pbind(
		\instrument, \stereoSampler,
		\bufnum, ~buffers[1],
		\amp,2,
		\octave, Pxrand([3], inf),
		\rate, 1,
		\start, Pfunc{ mx.getSynchronous.linlin(0.0,1.0,0,1) * (1 + 0.05.rand)},
		\note, Pseq([33-2], inf),
		\attack, 0.07,
		\decay, 0.2,
		\sustain,0.1,
		\release,0.2,
		\dur, Pseq([0.125] , inf)
		// \dur, Pfunc{ my.getSynchronous.linlin(0.0,1.0,0.125,0.2)},
	)
);

Pdef(\a).play(quant:[0.15]);
)


(//tim swim
	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);

Pdef(\a,
	Pbind(
		\instrument, \stereoSampler,
		\bufnum, ~buffers[6],
		\amp,5,
		\octave, Pxrand([3], inf),
		\rate, 1.01,
		\root, Pseq([0,-2].stutter(8), inf),
		\start, Pseq([0.1,25.3,59.3,64.4]/100, inf),
		\note, Pseq([33,33,33.6,33.6], inf),
		\attack, 0.07,
		\sustain,0.5,
		\release,0.3,
		\dur, Pseq([0.5] , inf)
	)
);

Pdef(\b,
	Pbind(
   \instrument, \pluckedString,
	\dur, Pseq([0.9,1.1], inf),
	\note, Pseq([0,-8,-5,0].stutter(1), inf),
	\root, Pseq([2,0].stutter(4), inf),
    \octave, Pseq([3], inf),
	\amp, Pexprand(0.5, 0.7) * 0.5,
	\attack, Pseg(Pseq([0.001, 0.1], inf), Pseq([3, 3], inf), \linear, inf),
    \decay, Pfunc{ mx.getSynchronous.linlin(0.0,1.0,1.5,0.2)},
	\phs, Pseg(Pseq([5, 20], inf), Pseq([10, 10], inf), \sine, inf),
	\filterFreq, Pfunc{ my.getSynchronous.linlin(0.0,1.0,100,10000)},//Pseg(Pseq([1000, 4000], inf), Pseq([30, 30], inf), \sine, inf),
	\filterRes, Pseg(Pseq([0.3, 0.7], inf), Pseq([20, 20], inf), \sine, inf),
    \pan, Pwhite(-0.7, 0.7)
	)
);




Pdef(\a).play(quant:[0.1]);
Pdef(\b).play(quant:[0.1]);
)



(//tim hi
	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);

Pdef(\a,
	Pbind(
		\instrument, \monoSampler,
		\bufnum, ~buffers[40],
		\amp,1,
		\octave, Pxrand([3], inf),
		\rate, 1,
		\root, Pseq([0].stutter(8), inf),
		\start, Pfunc{ mx.getSynchronous.linlin(0.0,1.0,0.2,0.75) * (1 + 0.03.rand)},
		\note, Pseq([33], inf),
		\attack, 0.07,
		\decay,0.1,
		\sustain,0.04,
		\release,0.112,
		\dur, Pseq([0.5,0.5,0.5,0.5] * 0.25, inf)
	)
);
Pdef(\b,
	Pbind(
		\instrument, \monoSampler,
		\bufnum, ~buffers[40],
		\amp,1,
		\octave, [2,3],
		\rate, 1,
		\root, Pseq([0].stutter(8), inf),
		\start, Pfunc{ mx.getSynchronous.linlin(0.0,1.0,0.75,0.2) * (1 + 0.03.rand)},
		// \start, Pfunc{ mx.getSynchronous.linlin(0.0,1.0,0.55,0.65) * (1 + 0.05.rand)},
		\note, Pseq([33-12], inf),
		\attack, 0.07,
		\sustain,0.03,
		\release,0.1,
		\dur, Pseq([0.125] , inf)
	)
);




Pdef(\a).play(quant:[0.1]);
Pdef(\b).play(quant:[0.1]);
)
