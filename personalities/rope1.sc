var m = ~model;


SynthDef(\woodBamboo, {
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
// intial state
//------------------------------------------------------------
~init = ~init <> {
m.ptn.postln;

	Pdef(m.ptn,
		Pbind(
			\instrument, \woodBamboo,
			// \dur, Pseq([0.25, 0.25, 0.5, 0.25, 0.25, 0.5] * 0.5, inf),
			\note, Pxrand([0,7], inf),
			\octave, 4,
			\root, Pseq([0,4,-3].stutter(40), inf),
			// \ringTime, Pwhite(0.05, 0.8),
		    \ringMix, Pwhite(0.6, 0.9),
		    \noiseMix, Pwhite(0.1, 0.4),
		    \amp, Pwhite(0.5, 0.8),
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

	// m.com.root.postln;
	// Pdef(m.ptn).set(\root, m.com.root);
};

~onHit = {|state|
};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linexp(0,2.4,1,0.07);
	var ring = m.accelMassFiltered.linexp(0,2,0.02,2);
	// var start = m.accelMass.linlin(0,0.5,0.5,0.8);
	// var amp = m.accelMass.linlin(0,1,0,4);
	// Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\ringTime, ring);
	// Pdef(m.ptn).set(\start, start);

	// Pdef(m.ptn).set(\filtFreq, m.accelMassFiltered.linexp(0,4,180,14000));
	//
	if(dur < 0.8,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.1);
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
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	[m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
