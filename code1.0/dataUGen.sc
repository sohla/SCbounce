(
	SynthDef(\data, { |out=0|
		SendReply.kr(Impulse.kr(10), '/value', BrownNoise.kr(30), 1905);
	}).add;

	SynthDef(\gen, { |out=0, freq=440, gate=1, amp=0.3, sustain=0.2, pan=0, patch=1, attack = 0.001, lpff = 10|

		var lpf = LPF.kr(freq,lpff);
		var sig = SinOsc.ar(lpf,0,1.0);
		var env = EnvGen.kr(Env.adsr(attack, sustain, sustain, 1.3), gate, doneAction:2);
		Out.ar(out, Pan2.ar(sig, pan, env * amp));
	}).add;

)

(
	a = Synth.head(s,\data);
	b = Synth.head(s,\gen);

	o = OSCFunc({ |msg| 
		b.set(\freq,160 + msg[3]); 
	}, '/value');
)
b.set(\lpff,100);

(
	a.free;
	b.free;
	o.free;
)



OSCBus