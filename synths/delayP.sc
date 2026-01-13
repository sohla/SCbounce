(
p = ExampleFiles.child;
b = Buffer.read(s, p); // remember to free the buffer later.

SynthDef(\help_PlayBuf, { |out = 0, bufnum = 0, pch=0|
	var sig= PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum), doneAction: Done.freeSelf);
	var maxdel=0.05;
	var rate = pch.midiratio - 1 / maxdel;
	var phs = LFSaw.ar(rate.neg, [1,0]).range(0,maxdel);
	var env = SinOsc.ar(rate, [3pi/2,pi/2]).range(0,1).sqrt;
	var del = DelayC.ar(sig, maxdel, phs) * env;
	del = del.sum!2 * \amp.kr(0.35);

    Out.ar(out, del)
}).add;
)

Synth(\help_PlayBuf, [\out, 0, \bufnum, b, \pch,0]);

(
Synth(\help_PlayBuf, [\out, 0, \bufnum, b, \pch,0]);
Synth(\help_PlayBuf, [\out, 0, \bufnum, b, \pch,-2]);
Synth(\help_PlayBuf, [\out, 0, \bufnum, b, \pch,2]);
)