var m = ~model;

	SynthDef(\wingChimes1, {
		|freq = 1000, pulseFreq = 10, amp = 0, rq = 0.001, att = 0.03, dec = 1.3, sus = 0, rel = 2, gate = 1, numHarms = 200|
		var snd, env;
		env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);
		snd = BPF.ar(
			in: WhiteNoise.ar(Select.ar(0,
				[
					Blip.ar(pulseFreq, numHarms, 0.5),
					LFPulse.ar(pulseFreq,0,0.5) * 0.01
				]
			)),
			freq: [freq, freq + 5],
			rq: Lag.kr(rq, 1));
		snd = snd * env * Lag.kr(amp, 1) * 100;
		snd = Clip.ar(snd, -0.5, 0.5);
		Out.ar(0, snd);
	}).add;

//------------------------------------------------------------

~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\instrument, \wingChimes1,
			\note, Prand([0,4,7,11], inf),
			\octave, Pwhite(3,6),
			\root, Pseq([0,7,3,0,7,4].stutter(48* 2),inf),
			\pulseFreq, Pwhite(3, 7),
			\numHarms, 30,
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

	var dur = 0.4 * 2.pow(m.accelMassFiltered.linlin(0,4,0,4).floor).reciprocal;
	var rq = m.accelMassFiltered.linexp(0,4,0.1,0.0005);
	var amp = m.accelMassFiltered.linexp(0,4,0.1,10);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\rq, rq);
	Pdef(m.ptn).set(\amp, amp);

	if(m.accelMass > 0.15,{
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
	// [m.rrateMass * 0.1, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};

