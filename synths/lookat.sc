(
Ndef(\x,{
	a = SinOsc.ar([37,37.1], Ndef(\x).ar * LFNoise1.ar(0.1,3) ,LFNoise1.ar(3,2)).tanh;
	9.do{
		a = AllpassL.ar(a,0.3,{0.2.rand+0.1}!2,5)
	};
	a.tanh * 0.5
}).play
)


(
	Ndef(\sinSynth, { |freq, gate = 1, att = 0.02, dec = 0.1, sus = 1.0, rel = 0.4, amp = 1.0|
		SinOsc.ar(freq, 0, 0.2) * amp * EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2)!2
	});


	~degree = PatternProxy(Pn(Pseries(0, 1, 8), inf));
	~dur = PatternProxy(Pn(0.25, inf));


	Ndef(\b, Pbind(
		\instrument, Ndef(\sinSynth).source,
		\dur, ~dur,
		\octave, 4,
		\degree, ~degree
		)
	).play;
)
~degree.source = Pwrap(Pseries(0, 3,inf),0,7);
~dur.source = 0.3



(
Ndef(\x,{
	a = SinOsc.ar([40,40.5], Ndef(\x).ar * LFNoise1.ar(0.1,2) ,LFNoise1.ar(MouseY.kr(0.2,19),MouseX.kr(0.1,5))).tanh;

	9.do{
		a = AllpassL.ar(a,0.3,{0.2.rand+0.1}!2,5)
	};

	a.tanh * 0.3;

}).play;
)


(
SynthDef(\fbdrone,{|out = 0, freq = 60, amp = 1.0, nf = 1.0, na = 3.0| // (0.1,2) (0.1/5)
	var sig = SinOsc.ar([freq, freq + (freq * 0.02)], LocalIn.ar(2) * LFNoise1.ar(0.1,2) ,LFNoise1.ar(MouseY.kr(0.2,19),MouseX.kr(0.1,5))).tanh;
	9.do{
		sig = AllpassL.ar(sig,0.3,{0.2.rand+0.1}!2,5)
	};
	sig = sig.tanh * 0.2;

	LocalOut.ar(sig);
	Out.ar(out, sig * amp);
}).play;
)

(
Ndef(\y,{
	a = SinOsc.ar(30, Ndef(\x).ar * MouseX.kr(0,2pi),0.3);
	9.do{
		a = AllpassL.ar(a,0.03,(0.02.rand+0.1));
	};

	a.tanh;

}).play;
)

(
Ndef(\x,{
	a = SinOsc.ar(165, LocalIn.ar(2) * 1,0.5).tanh;

	LocalOut.ar(a.tanh);
	a.tanh;

}).play;
)




(
{
	p = MouseX.kr(40,60);
	z = BPF.ar(WhiteNoise.ar(1),p,0.1,4);
	z + AllpassN.ar(z, 0.02, p.reciprocal*[1,1.01] * 2, 0.2)
}.play

)


{l=LocalIn.ar(2);k=LFSaw.ar(l,0,1,1);j=k.range(1.0,MouseX.kr(1,7, lag:2));s=PitchShift.ar(SinOscFB.ar(j**[2,2.03],k),[0.042,0.043],j* MouseY.kr(0.1,1.0, lag:2.0));LocalOut.ar(s.tanh);s*0.8}.play;

{l = LocalIn.ar(2);k=LFSaw.ar(l,0,l,1);j=k.range(3.0,4.0);s=PitchShift.ar(SinOscFB.ar((j*MouseX.kr(20,60)),k),[0.14,0.141],j);5.do{s=s.tanh+(s*0.1)};LocalOut.ar(s);s*0.05}.play;


{VarSaw.ar(MouseX.kr(30,120),0,MouseY.kr(0.1,0.9)*[1,0.9])}.play

play{BPF.ar(VarSaw.ar(LFNoise1.kr(3,40,200),0,0.25)+PinkNoise.ar(0.1),LFNoise2.kr(12,700,1000),0.3)!2};//#supercollider


play{VarSaw.ar((Hasher.ar(Latch.ar(SinOsc.ar((1..4)!2),Impulse.ar([5/3,5])))*100+100).round(MouseX.kr(50,100)),0,0.1)/10}//



(
Ndef(\wa,{
	x=0;
	a = (50..54)*MouseX.kr(1,2,4);
	(a).do{|f|
		f=f/2;
		x=SinOsc.ar(
			f+[0,1],
			x*LFTri.kr(6,2).range(0.02,MouseY.kr(0.1,2.5))
		)
	};
		x * 0.3;

}).play(fadeTime:2);
)


(

Ndef(\gendysub, { |out=0, ad=4, dd=3, adp=1, ddp=0.01, minf=35, maxf=37, as=0.25, ds=0.4, suba=0.2|

	var gen = Gendy1.ar(ad, dd, adp, ddp, minf, maxf, as, ds, mul: 0.1);

	var splay = Splay.ar( {Gendy1.ar(ad, dd, adp, ddp, Lag.kr(minf * MouseX.kr(1,1.6),1), Lag.kr(maxf* MouseX.kr(1,1.6),1), as* MouseX.kr(1,2), ds, mul: 0.1)} !10);
	var sub = SinOsc.ar([minf, maxf] * MouseX.kr(1,1.6),[0,pi/5],MouseY.kr(0,1));
	Out.ar(out, splay + sub);
}).play(fadeTime:4);
)


