var m = ~model;


SynthDef(\funMelody, {
    |freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2, rm = 0.5|
    var osc1, osc2, osc3, env, filter, output;
	var a = rm;
	var b = Array.linrand(1,0.0,1.0-rm);
	var c = 1.0 - b - a;
	// [a,b,c].flat

    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    osc1 = Saw.ar(freq, 1.5);
    osc2 = Pulse.ar(freq * 0.99, 0.5, 0.5);
    osc3 = SinOsc.ar(freq * 1.01, 0, 1.5);
    output = Mix([osc1, osc2, osc3]) * env * amp;
    filter = RLPF.ar(output, filtFreq, filtRes);
	filter = ([filter, DelayN.ar(filter, 0.02, 0.02)] * 1.5).tanh;
    Out.ar(0, filter);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \funMelody,
			\note, Pseq([0,7,10,5].stutter(4), inf),
			\octave,Pseq([4,5].stutter(2),inf),
			// \root, Pseq([0].stutter(32), inf),
			\envAtk,0.002,
			\envDec, Pwhite(0.2, 0.1, inf),
			\envSus, 0.0,
			\envRel,Pkey(\octave).squared * 0.1,
    		\amp, 0.1,
			\rm, 0.5,
    		\filtRes, 0.4,
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.25);
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

~onEvent = {|e|
	Pdef(m.ptn).set(\root, m.com.root);
};

~onHit = {|state|
};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	// var dur = 0.5 * 2.pow(m.accelMassFiltered.linexp(0,3,0,5).floor).reciprocal;
	var dur = 0.5 * 2.pow(m.accelMassFiltered.linlin(0,3,0,4).floor).reciprocal;
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\filtFreq, m.accelMassFiltered.linexp(0,4,180,14000));

	if(m.accelMass > 0.12,{
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
	[m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

