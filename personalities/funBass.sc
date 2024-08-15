var m = ~model;


SynthDef(\funBass, {
    |freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2, rm = 0.5|
    var osc1, osc2, osc3, env, filter, output;
	var a = rm;
	var b = Array.linrand(1,0.0,1.0-rm);
	var c = 1.0 - b - a;
	// [a,b,c].flat

    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    osc1 = Saw.ar(freq, 1);
    osc2 = Pulse.ar(freq * 0.99, 0.5, 1);
    osc3 = SinOsc.ar(freq * 1.01, 0, 1);
    output = Mix([osc1, osc2, osc3]) * env * amp;
    filter = RLPF.ar(output, filtFreq, filtRes);
	filter = ([filter.distort, filter.tanh] * 2.5);
    Out.ar(0, filter);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {
m.ptn.postln;
	Pdef(m.ptn,
		Pbind(
			\instrument, \funBass,
			\note, Pseq([0,12,10,7].stutter(4), inf),
			\octave,Pseq([2,3].stutter(2),inf),
			\root, Pseq([0,3,5,-2].stutter(16), inf),
			\envAtk,0.003,
			\envDec, Pwhite(0.06, 0.3, inf),
			\envSus, 0.0,
			\envRel,Pkey(\octave).squared * 0.07,
    		\amp, 0.1,
			\rm, Pwhite(0.1,0.9),
    		\filtRes, Pwhite(0.4,0.6),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.25);
};


//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};

~onHit = {|state|
};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = 0.5 * 2.pow(m.accelMassFiltered.linlin(0,3,0,4).floor).reciprocal;
	// dur.postln;
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\filtFreq, m.accelMassFiltered.linexp(0,4,280,4000));

	if(m.accelMass > 0.07,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).resume(quant:0.25);
		});
	},{
		if( Pdef(~model.ptn).isPlaying,{
			Pdef(~model.ptn).pause();
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
	[m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

