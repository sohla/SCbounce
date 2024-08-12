(
	SynthDef(\mouseXYBuffer, { |bufnum|
		BufWr.kr(
		[MouseX.kr(), Lag2.kr(Slope.kr(MouseX.kr()).abs)],
			bufnum,
			Phasor.ar(
				0,
				BufRateScale.kr(bufnum),
				0,
				BufFrames.kr(bufnum)
			)
		);
	}).add;

	SynthDef(\bufferReader, { |out=0, bufnum|
		var sig = BufRd.ar(
			2,
			bufnum,
			Phasor.ar(
				0,
				BufRateScale.kr(bufnum),
				0,
				BufFrames.kr(bufnum)
			)
		);
		Out.kr(out, sig);
	}).add;
)


(
	b = Buffer.alloc(s, s.sampleRate * 0.1, 2);
)

(
	a = Synth(\mouseXYBuffer, [\bufnum, b]);
	b = Synth.after(a, \bufferReader, [\bufnum, b]);
)
s.scope

