

(
Pdef(\pat1).set(\octave,4);
Pdef(\pat1).set(\dur,0.25);
Pdef(\pat1,
	Pbind(
        \degree, Pseq([0, 7, 4, 3, 9, 5, 1, 4], inf),
		\gtranspose, Pstutter(8,Pseq([0,3,8,-2],inf),inf),
		\amp, Pexprand(0.1,0.4,inf),
		\pan, 0
));

~spec = ControlSpec(0,3,step:1);
~window = Window.new("mixers", Rect(10, 100, 320, 60));
~window.view.decorator = FlowLayout(~window.view.bounds, 2@2);
EZSlider(~window, 310@20, "low part", \amp, { |ez| 
	Pdef(\pat1).set(\dur,Array.geom(4, 1, 2).at(~spec.map(ez.value)).reciprocal); 
}, 0.5);
~window.front.onClose_({ Pdef(\pat1).stop });

Pdef(\pat1).play;
)

(

~spec = ControlSpec(-1,1,step:0.01);
~spec2 = ControlSpec(0,3,step:1);

~val = (a:0.8);


~window = Window.new("mixers", Rect(10, 100, 320, 60));
~window.view.decorator = FlowLayout(~window.view.bounds, 2@2);
EZSlider(~window, 310@20, "low part", \amp, { |ez| 
	//Pdef(\pat1).set(\dur,Array.geom(4, 1, 2).at(~spec.map(ez.value)).reciprocal); 
	~val.a = ~spec.map(ez.value);

}, 0.5);
~window.front.onClose_({ Pdef(\pat1).stop });


	Pbind(
        \degree, Pseq([0, 7, 4, 3, 9, 5, 1, 4], inf),
		\gtranspose, Pstutter(8,Pseq([0,3,8,-2],inf),inf),
		\amp, Pexprand(0.1,0.4,inf),
		\pan, Penvir(~val,Pfunc{~a}),
		\dur, 0.125,
		\legato, Pwhite(0.1,5,inf)
).play;
)




SynthDef(\xylo, { |out=0, freq=440, gate=1, amp=0.3, sustain=0.2, pan=0|
	var sig = StkBandedWG.ar(freq, instr:1, mul:3);
	var env = EnvGen.kr(Env.adsr(0.0001, sustain, sustain, 1.3), gate, doneAction:2);
	Out.ar(out, Pan2.ar(sig, pan, env * amp));
}).add;

Synth(\xylo);

Pbind(\instrument, \xylo, \freq, Pseq(({|x|x+60}!13).mirror).midicps, \dur, 0.2).play

Synth(\filtSaw)



(
s = Server.local.waitForBoot({
	~bus = Bus.audio(s,2);
	SynthDef(\reverb_ef, {
		arg		amp=1, lPos=0, mix=0.085, revTime=1.8, preDel=0.1, in, out;
		var		sig, verbSig, totalSig, outSig;

		//no wacky values please
		mix = mix.clip(0,1);

		sig = In.ar(in, 2);

		//pre-delay
		verbSig = DelayN.ar(sig, preDel, preDel);

		totalSig = 0;
		12.do{
			verbSig = AllpassN.ar(verbSig, 0.06, {Rand(0.001,0.06)}!2, revTime);
			verbSig = LPF.ar(verbSig, 4500);
			totalSig = totalSig + verbSig;
		};

		//dry/wet mix
		totalSig = XFade2.ar(sig, totalSig, mix.linlin(0,1,-1,1));

		outSig = totalSig * amp;
		Out.ar(out, outSig);
	}).add;

	SynthDef(\filtSaw, {
		arg		freq=440, detune=3.0, atk=6, sus=4, rel=6, curve1=1, curve2=(-1),
				minCf=30, maxCf=6000, minRq=0.005, maxRq=0.04,
				minBpfHz=0.02, maxBpfHz=0.25,
				lowShelf=220, rs=0.85, db=6,
				gate=1, amp=1, spread=1.0, out=0;
		var sig, env;
		env = EnvGen.kr(Env([0,1,1,0],[atk,sus,rel],[curve1,0,curve2]), gate, levelScale:amp, doneAction:2);
		sig = Saw.ar(
			freq +
			LFNoise1.kr({LFNoise1.kr(0.5).range(0.15,0.4)}!8).range(detune.neg,detune));
		sig = BPF.ar(
			sig,
			LFNoise1.kr({LFNoise1.kr(0.13).exprange(minBpfHz,maxBpfHz)}!8).exprange(minCf, maxCf),
			LFNoise1.kr({LFNoise1.kr(0.08).exprange(0.08,0.35)}!8).range(minRq, maxRq)
		);
		sig = BLowShelf.ar(sig, lowShelf, rs, db);
		sig = SplayAz.ar(4, sig, spread);
		sig = sig * env * 2;
		Out.ar(out, sig);
	}).add;

	~cluster = {
		var trnsp, bund;
		bund = s.makeBundle(false, {});
		trnsp = rrand(-7,7);
		Array.fill(exprand(4,14).round.postln, {[1,2,3,4,6,8,12,16].wchoose([7,6,5,4,3,3,1].normalizeSum)}).do{
			|i|
			var cfLo;
			cfLo = (([23,35,47,50,52,59,61,63,64,76,78].choose) + trnsp).midicps * ((1..8).choose);

				bund = s.makeBundle(false, {
					Synth(
						\filtSaw,
						[
							\freq, i,
							\detune, 0,
							\minBpfHz, 0.01,
							\maxBpfHz,i.expexp(1.0,16.0,0.1,16.0),
							\minRq, 0.003,
							\maxRq, exprand(0.008,0.08),
							\minCf, cfLo,
							\maxCf, cfLo * [1,1.1,1.5].wchoose([0.87,0.1,0.03]),
							\amp, exprand(0.15,0.25),
							\atk, exprand(0.7,8),
							\rel, 5,
							\sus, rrand(6,10.0),
							\spread, exprand(1.5,8.0),
							\out, ~bus,
						],
					)
				},
				bund
			)
		};

		//schedule on an integer time value
		SystemClock.schedAbs(
			(thisThread.seconds+1.0).round(1.0),
			{
				bund = s.makeBundle(nil, {}, bund);
				nil;
			}
		);
	};
});
)

//start clusters
(
t = Task {
	//instantiate reverb synth
	Synth(\reverb_ef, [\in, ~bus, \out, 0]);

	{
		~cluster.value;
		rrand(5.5,12.0).wait;
	}.loop;
}.start;
)

//stop clusters
t.stop;


(
SynthDef(\help_dwgplucked, { |out=0, freq=440, amp=0.5, gate=1, c3=20, pan=0, attack = 0.001|
    var env = Env.new([0,1, 1, 0],[attack,0.006, 0.005],[5,-5, -8]);
    var inp = amp * LFClipNoise.ar(2000) * EnvGen.ar(env,gate);
    var son = DWGPlucked.ar(freq, amp, gate,MouseX.kr(),1,c3,inp);
    DetectSilence.ar(son, 0.001, doneAction:2);
    Out.ar(out, Pan2.ar(son * 0.1, pan));
}).add;
)

(
	Pbind(
		\instrument,\help_dwgplucked,
		\gtranspose, [-12,0,12],
        \degree, Pseq([0, 7, 4, 3, 9, 5, 1, 4], inf),
		\dur, 0.125,
		\legato, Pwhite(0.1,10,inf),
		 \c3 , Pseq(Pseries(10,40,20).asArray,inf).trace,
		\attack, 1,
		\pan,Pwhite(-1,1)
).play;
	)




(1..10)


(
z = { exprand(100.0, 5000.0) } ! 20;
Ndef(\x, {
	var in = Decay.ar(Dust.ar(MouseX.kr(0.0001, 0.1, 1) * z, 10), 0.3) * PinkNoise.ar(0.4 ! (z.size div: 3), 1);
	z = z * LFNoise1.ar(0.2 ! z.size).range(1, 2) * LFNoise0.ar(20 ! z.size).exprange(1, 1.8);
	Splay.ar(ComplexRes.ar(in, z, 10 / z)) * 2
}).play
)


// this started out as a comparison between BPF and ComplexRes


(
z = [253.12, 2881.123, 2883.4, 1002.2, 882.01];
Ndef(\x, {
	var in = Decay.ar(Dust.ar(5, 100), 0.3) * PinkNoise.ar(0.4, 1);
	ComplexRes.ar(in, z, 10 / z).sum
}).play
)


(
z = [253.12, 2881.123, 2883.4, 1002.2, 882.01];
Ndef(\x, {
	var in = Decay.ar(Dust.ar(5, 100), 0.3) * PinkNoise.ar(0.4, 1);
	BPF.ar(in, z, (10 / z) * 2).sum
}).play
)

(
z = [253.12, 2881.123, 2883.4, 1002.2, 882.01];
Ndef(\x, {
	var in = Decay.ar(Dust.ar(5, 100), 0.3) * PinkNoise.ar(0.4, 1);
	z = z * LFNoise0.ar(13).range(1, 2);
	ComplexRes.ar(in, z, 10 / z).sum
}).play
)


(
z = [253.12, 2881.123, 2883.4, 1002.2, 882.01];
Ndef(\x, {
	var in = Decay.ar(Dust.ar(5, 100), 0.3) * PinkNoise.ar(0.4, 1);
	z = z * LFNoise0.ar(13).range(1, 2);
	BPF.ar(in, z, (10 / z) * 2).sum
}).play
)


(
SynthDef(\Synth3,
	{arg ress = 0;
		var klank, env;
		klank = Klank.ar(`[{Rand(70,21000)}!7, {Rand(0.128,0.700)}!7],BrownNoise.ar(0.7));
		klank = klank;
		env = EnvGen.kr(Env.perc(0.07, ress), doneAction:2);
		Out.ar(0, klank*env.dup*0.0128);
}).add;
)


{inf.do{x = rrand(0.01,0.7); Synth(\Synth3, [\ress, x+(7*x)]); x.wait;}}.fork








(
SynthDef(\help_pindex, { | out, amp=0.1, freq=440, gate=1 |
    var son = Saw.ar(freq * [0.99, 1, 1.01]).mean;
    son = son * EnvGen.ar(Env.adsr, gate: gate, doneAction:2);
    Out.ar(out, son.dup * amp);
}).add;
)

(		
var data = Scale.major;
var indices = [5, 5, 2, 5, 4, 6, 0];
Pbind(
    \instrument, \help_pindex,
    \choice, Pseq(indices, inf),
    \degree, Pindex(data, Pkey(\choice), inf),
    \dur, 0.5
).play
)


Scale.directory