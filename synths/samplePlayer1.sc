


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
~sampleFolder = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 2July2025/converted");
~buffers = ~sampleFolder.entries.collect({ |path|
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


