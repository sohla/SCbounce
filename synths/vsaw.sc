(
SynthDef(\vsaw, {
    |out =0,freq = 2000, warbleSpeed = 8, warbleDepth = 100,
        chirpRate = 1, chirpDur = 0.1, noiseMix = 0.2,
        amp = 0.3, pan = 0, gate=1|

    var sig = 	Splay.arFill(4,{|i|
		VarSaw.ar(
			freq + ( (i+1).reciprocal),
			i,
			 i * MouseX.kr(0,0.02), //width
			0.2)
		});


    sig = sig * EnvGen.kr(Env.perc(0.07, 0.3), gate, doneAction:2);
    sig = Pan2.ar(sig, pan);

    sig * amp;
	Out.ar(out, sig);
}).add;
)

(
Pbindef(\magpiePattern,
    \instrument, \vsaw,
    \dur, 0.2,
    \freq, Pexprand(50, 90, inf),
    \amp, Pexprand(0.05, 0.15, inf),  // Random amplitude
    \pan, Pwhite(-1.0, 1.0, inf),    // Random panning
	\chirpRate, 0.1
).play;
)