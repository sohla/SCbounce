

(
~sampleFolder = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 25June2024/converted");
~buffers = ~sampleFolder.entries.collect({ |path|
    Buffer.read(s, path.fullPath);
});
)

(
SynthDef(\folderSampler, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=0.2, gate=1,cutoff=20000, rq=1|

	var lr = rate * BufRateScale.kr(bufnum) * (freq/440.0);
    var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.003], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = RLPF.ar(sig, cutoff, rq);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;
)

(
Pdef(\a,
	Pbind(
		\instrument, \folderSampler,
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
		\instrument, \folderSampler,
		\bufnum, ~buffers[40],
		\amp,5,
		\octave, Pxrand([3], inf),
		\rate,1,
		\start, Pseg(Pseq([0.3,0.7],inf), 2, \linear),
		\note, Pxrand([34], inf),
		\release,0.1,
		\dur, Pseq([0.7,Rest(0.3)] * 0.3, inf)
	)
);

Pdef(\a).play(quant:[0.15]);
)



