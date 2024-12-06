var m = ~model;

	SynthDef(\wingChimes3, {
		|freq = 1000, pulseFreq = 10, amp = 0, rq = 0.001, att = 0.9, dec = 1.3, sus = 0, rel = 5, gate = 1, numHarms = 200|
		var snd, env;
		env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);
		snd = BPF.ar(
			in: WhiteNoise.ar(
					Blip.ar(pulseFreq, numHarms, 0.7) + 
					LFPulse.ar(pulseFreq,0,1,0.2) 
			),
			freq: [freq, freq + 5],
			rq: Lag.kr(rq, 1));
		snd = snd * env * Lag.kr(amp, 1) * 20;
		snd = Clip.ar(snd, -0.5, 0.5);
		Out.ar(0, snd);
	}).add;

//------------------------------------------------------------

~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\instrument, \wingChimes3,
			\note, Prand([0,7,11], inf),
			\octave, Pwhite(5,6)-1,
			\root, Pseq([0,3,-4, -1, 3].stutter(24),inf),
			\pulseFreq, Pwhite(3, 7),
			\numHarms, 100,
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);
	Pdef(m.ptn).play(quant:[0.1]);
};

~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};
//------------------------------------------------------------
~next = {|d|

	var dur = 0.3;// * 2.pow(m.accelMassFiltered.linlin(0,4,0,4).floor).reciprocal;
	var rq = m.accelMassFiltered.linexp(0,4,0.1,0.0005);
	var amp = m.accelMassFiltered.linexp(0,4,0.05,1);
//	var part = m.accelMassFiltered.linlin(0,3,0,2).floor.asInteger;

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\rq, rq);
	Pdef(m.ptn).set(\amp, amp);

	// if(part == 0, { Pdef(m.ptn).set(\note, Prand([0], inf)) });
	// if(part == 1, { Pdef(m.ptn).set(\note, Prand([0,7], inf)) });
	// if(part == 2, { Pdef(m.ptn).set(\note, Prand([0,7,11], inf)) });

	if(m.accelMass > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:[0.1,0,0,0]);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});
};

~nextMidiOut = {|d|
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	// [m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	[m.rrateMass * 0.1, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
