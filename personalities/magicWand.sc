var m = ~model;


SynthDef(\glockenspiel, {
    |freq = 440, amp = 0.5, decay = 1, pan = 0, hardness = 1|
    var exciter, env, output;

    exciter = WhiteNoise.ar(amp) * Decay2.ar(Impulse.ar(0, 0, amp), 0.005, 0.02);
    env = EnvGen.ar(Env.perc(0.001, decay), doneAction: Done.freeSelf);
    output = DynKlank.ar(`[
        [1, 4.08, 10.7, 18.8, 24.5, 31.2] ,
        [1, 0.8, 0.6, 0.4, 0.2, 0.1]* 0.5,
        [1, 0.8, 0.6, 0.4, 0.2, 0.1]
    ], exciter, freq, 0, hardness);
    output = output * env;
    output = Pan2.ar(output, pan);
    Out.ar(0, output);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\instrument, \glockenspiel,
			\note, Pseq([-5,0,4,7,-12,4],inf),
			\root, Pseq([0,5,-2,3,-4,1,-5].stutter(32),inf),
			\decay, 3.5,
			\hardness, 3.5,
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).set(\dur,0.2);
	Pdef(m.ptn).play();
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

	var oct = m.accelMassFiltered.linlin(0,5,2,5).floor;
	var dur = 0.5 - m.accelMassFiltered.squared.linlin(0,3,0,0.43);
	Pdef(m.ptn).set(\dur, dur);
	// Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 2 * m.rrateMassThreshold.reciprocal).reciprocal);
	// Pdef(m.ptn).set(0.2);
	Pdef(m.ptn).set(\octave, 4 + oct);
	Pdef(m.ptn).set(\amp, oct.linlin(2,5,0.1,0.2));

	if(dur < 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,5,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};




