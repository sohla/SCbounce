(


SynthDef(\pullstretch, {|amp = 0.8, buffer = 0, envbuf = -1, pch = 1.0, div=1|
	var pos;
	var mx,my;
	var sp;
	var mas;
	var len = BufDur.kr(buffer) / div;
	mx = LFSaw.kr( (1.0/len) * MouseX.kr(0.05,1.0) ,1,0.5,0.5);

	my = MouseY.kr(0.01,1,1.0);//splay


	sp = Splay.arFill(12,
			{ |i| Warp1.ar(1, buffer, mx, pch,my, envbuf, 8, 0.1, 2)  },
			1,
			1,
			0
		) * amp;

	mas = HPF.ar(sp,245);

	Out.ar(0,mas);
}).send(s);

)


(
~sampleFolder = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 25June2024/converted");
~buffers = ~sampleFolder.entries.collect({ |path|
    Buffer.read(s, path.fullPath);
});
)

(

Synth(\pullstretch,[\buffer,~buffers[6],\pch,0.midiratio, \amp,0.4, \div, 4]);
Synth(\pullstretch,[\buffer,~buffers[6],\pch,12.midiratio, \amp,0.3, \div, 4]);
Synth(\pullstretch,[\buffer,~buffers[6],\pch,7.midiratio, \amp,0.2, \div, 4]);
)

(
Synth(\pullstretch,[\buffer,~buffers[10],\pch,0.midiratio, \amp,0.2, \div, 16]);
Synth(\pullstretch,[\buffer,~buffers[10],\pch,4.midiratio, \amp,0.2, \div, 16]);
Synth(\pullstretch,[\buffer,~buffers[10],\pch,7.midiratio, \amp,0.2, \div, 16]);
)


(
Synth(\pullstretch,[\buffer,~buffers[40],\pch,0.midiratio, \amp,0.6, \div,1]);
)

s.meter
y.free;
c.free;


(
SynthDef(\synth2211, { |out=0, gate=1, freq=100, rel=0.1, amp=0.1, shp= 0.09|
	var env = EnvGen.ar(Env.perc(rel.linlin(0.002,0.4,0.001,0.01), rel), gate, [1, 0.2, 0.04, 0.02], doneAction:0);
	var sig = DynKlang.ar(`[ [1,3,5,7] * freq * LFNoise2.ar(30).linlin(-1,1,0.98,1.02), env, [[0,pi,0],[pi, 0, pi]]], 1, 0) * 0.3;
	var sub = SinOsc.ar(freq * 0.5, [0,pi.half], 0.1 * env);
	var rev = CombL.ar(sig + sub, 0.2, [0.07, 0.075] * SinOsc.ar(freq).linlin(-1,1,1,1+shp), 0.2);
	DetectSilence.ar(rev, doneAction: Done.freeSelf);
	Out.ar(out, (DelayN.ar(sub,0.1,[0.07,0.09], 14) + rev) * amp * 0.7);
}).add;
)




(
Pdef(\a,
	Pbind(
		\instrument, \synth2211,
		\octave, Pxrand([3,4,5], inf),
		\note, Pseq([0], inf),
		\root, Pseq([0,3,-2,-4].stutter(24)-3, inf),
		\dur, Pxrand([0.2,0.2,0.2,0.1,0.1,0.2] * 2, inf),
		\rel, Pwhite(0.002, 0.9, inf),
		\amp, Pkey(\octave).linlin(3,6,1,0.2),
		\shp, Pwhite(0.9,0.002, inf),
	)
);

Pdef(\a).play(quant:0);

)
