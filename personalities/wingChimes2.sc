var m = ~model;


	SynthDef(\wingChimes2, {|freq = 1000, pulseFreq = 10, amp = 0.1, rq = 0.001, att = 0.23, dec = 1.3, sus = 0, rel = 3, gate = 1, numHarms = 200|
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
// intial state
//------------------------------------------------------------
~init = ~init <> {
	var pat1 = Pbind(
		\instrument, \wingChimes2,
		\note, Prand([-0.1], inf),
		\octave, Pseq([2,4], inf),
		\pulseFreq, Pwhite(1, 3),
		\numHarms, 10,
			\func, Pfunc({|e| ~onEvent.(e)}),
	\args, #[],

	);


	Pdef(m.ptn,pat1);

	Pdef(m.ptn).play(quant:[0.1]);
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	// m.com.root = e.root;
	// m.com.dur = e.dur;
	Pdef(m.ptn).set(\root, m.com.root);

};

~onHit = {|state|

};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = 0.4 * 2.pow(m.accelMassFiltered.linlin(0,4,0,4).floor).reciprocal;
	var rq = m.accelMassFiltered.linexp(0,4,0.01,0.0001);
	var amp = m.accelMassFiltered.linexp(0,4,1,30);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\rq, rq);
	Pdef(m.ptn).set(\amp, [amp * 2, amp] * 0.7);

	if(m.accelMass > 0.15,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).play(quant:[0.1,0,0,0]);
		});
	},{
		if( Pdef(~model.ptn).isPlaying,{
			Pdef(~model.ptn).stop();
		});
	});
};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 64 );
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	// [m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	[m.rrateMass, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};


//
//
//
//
// (
//
// var a = Pbind(
// 	\dur, 0.2,
// 	\note, Pseq([0,2,4,6,8,10], inf)
// );
//
// var b = Pbind(
// 	\dur, 0.3,
// 	\octave, 3,
// 	\note, Pseq([12,8,4,0], inf)
// );
//
// Pdef(\pd, Ppar([a,b], inf));
// Pdef(\pd).play();
// )