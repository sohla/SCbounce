var m = ~model;


//------------------------------------------------------------
SynthDef(\woodBamboo2, {
    |out, freq=1000, ringTime=0.1, ringMix=0.5, noiseMix=0.5, amp=0.5|
    var exciter, resonator, noiseSig, output;
    exciter = Impulse.ar(0);
    resonator = Klank.ar(
        `[
            [freq, freq*1.51, freq*2.37], // Resonant frequencies
            [1, 0.3, 0.1],             // Amplitudes
            [ringTime, ringTime*0.9, ringTime*0.8]  // Decay times
        ],
        exciter
    );
    noiseSig = LPF.ar(WhiteNoise.ar, 2000) * EnvGen.ar(Env.perc(0.001, 0.01));
    output = (resonator * ringMix) + (noiseSig * noiseMix);
    output = output * EnvGen.ar(Env.perc(0.01, ringTime * 2), doneAction: 2);
    Out.ar(out, Pan2.ar(output, 0, amp));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \woodBamboo2,
			\note, Pxrand([0,7,12], inf),
			\octave, 5,
		    \ringMix, Pwhite(0.6, 0.9),
		    \noiseMix, Pwhite(0.1, 0.4),
		    \amp, Pwhite(0.5, 0.6),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.25);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

~onEvent = {|e|
	Pdef(m.ptn).set(\root, m.com.root);
};

//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linexp(0,1.8,2,0.12);
	var ring = m.accelMassFiltered.linlin(0,2,1.8,3.8);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\ringTime, ring);

	if(dur < 1.3,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.1);
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
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	[m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};

