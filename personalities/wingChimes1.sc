var m = ~model;

m.rrateMassFilteredAttack = 0.3;
m.rrateMassFilteredDecay = 0.1;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.2;


SynthDef(\wingChimes1, {
	|freq = 1000, pulseFreq = 10, amp = 0, rq = 0.001, att = 0.03, dec = 1.3, sus = 0.8, rel = 1, gate = 1, numHarms = 20|
	var env = EnvGen.kr(Env.adsr(att, dec, sus, rel), gate: gate, doneAction: 2);
	var snd = BPF.ar(
		in: WhiteNoise.ar(Blip.ar(pulseFreq, numHarms, 0.7) + LFPulse.ar(pulseFreq,0,1,0.2)),
		freq: [freq, freq + 5],
		rq: Lag.kr(rq, 0.2));
	snd = snd * env * Lag.kr(amp, 0.2) * 20;
	snd = Clip.ar(snd, -0.5, 0.5);
	Out.ar(0, snd);
}).add;
//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \wingChimes1,
			\note, Prand([0,7,11], inf),
			\octave, Pwhite(1,3),
			\root, Pseq([0,3,-4, -1, 3].stutter(24),inf),
			// \pulseFreq, Pwhite(3, 7),
			\numHarms, 1,
			\func, Pfunc({|e| ~onEvent.(e)}),
			
			\type, \customEvent,
			\duration, 1.7,
			\sx, Pwhite(0,0),
			\sy, Pwhite(0,0),
			\ex, Pkey(\sx),
			\ey, Pkey(\sy),
			\startWidth, 3,
			\endWidth, 1,
			\startSize, 10,
			\endSize, 220,
			\shape, \circle,
			\rotation, Pseg(Pseq([-pi, pi], inf), 80, \linear, inf),
	  	// \startColor, Pfunc{|e|Color.hsv(e.octave.linlin(3,6,0.2,0.23),0.6,0.8)},
	  	\startColor, Pfunc{|e|Color.hsv(e.root.linlin(-4,3,0.0,0.49),0.9,0.9)},
			\endColor, Pfunc{|e|Color.hsv(e.root.linlin(-4,3,0.5,0.99),0.3,0.9).alpha_(0.0)},
			
      // \endColor: Color.blue.alpha_(0.4),
			\args, #[],
		);
	).play(quant:[0.1]);
};
//------------------------------------------------------------
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
	var dur = 0.3 * 2.pow(m.accelMassFiltered.linlin(0,4,0,4).floor).reciprocal;
	var rq = m.accelMassFiltered.linexp(0,4,0.1,0.0005);
	var amp = m.accelMassFiltered.linexp(0,4,0.05,1);
	var sp = m.accelMassFiltered.lincurve(0,2.5,1,5);
	var pf = m.accelMassFiltered.lincurve(0,2.5,1,12);
	var rr = 3.rrand(7);

	Pdef(m.ptn).set(\viewID, d.port);

	Pdef(m.ptn).set(\modulation, (
			type: \radial,
			freq: rr * 3,
			amp: 4,
			harmonics: 2
	));

	Pdef(m.ptn).set(\pulseFreq, 20);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\rq, rq);
	Pdef(m.ptn).set(\amp, amp * 2);
	if(m.accelMass > 0.04,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:[0.2,0,0,0]);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});
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

