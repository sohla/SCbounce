var m = ~model;


SynthDef(\glockenspiel, {
    |freq = 440, amp = 0.5, decay = 1, pan = 0, hardness = 1, mix=0.5, room=0.5|
    var exciter, env, sig;
		var freqs = [1, 4.08, 10.7, 18.8, 24.5, 31.2] * freq;
		var tone = SinOsc.ar(freq * [2,2.007], LFNoise2.ar(freq * 0.01,10),0.15);
    exciter = WhiteNoise.ar(0.01) * Decay2.ar(Impulse.ar(0, 0, amp), 0.005, 0.02);
    env = EnvGen.ar(Env.perc(0.003, decay), doneAction: Done.freeSelf);
    sig = DynKlank.ar(`[
        freqs,
        [1, 0.8, 0.6, 0.4, 0.2, 0.1]* 0.5 * LFCub.ar(3,0,0.4,0.5),
        [1, 0.8, 0.6, 0.4, 0.2, 0.1]
    ], exciter, 1, 0, hardness);
    sig = Pan2.ar(sig + tone, pan);
    sig = sig * env * amp;
    Out.ar(0, sig);
}).add;


//------------------------------------------------------------
~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\instrument, \glockenspiel,
			\note, Pseq([-5,0,4,7,-12,4],inf),
			\decay, 2.5,
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
	Pdef(m.ptn).set(\root, m.com.root);
};

//------------------------------------------------------------
~next = {|d|

	var oct = m.accelMassFiltered.lincurve(0,3,2,6,-1).floor;
	var dur = 0.23 - m.accelMassFiltered.linlin(0,2.5,0.001,0.14);
	var hardness = m.accelMassFiltered.linlin(0,2.5,0.2,0.9).clip2(0.91);
	var amp = m.accelMassFiltered.linexp(0,2.5,1,0.6);
	
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\octave, 2 + oct);
	Pdef(m.ptn).set(\amp, amp*0.13);
	Pdef(m.ptn).set(\hardness, 1 - hardness);

	if(m.accelMassFiltered > 0.1,{
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




