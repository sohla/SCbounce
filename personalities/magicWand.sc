var m = ~model;
m.midiChannel = 1;


SynthDef(\glockenspiel, {
    |freq = 440, amp = 0.5, decay = 1, pan = 0, hardness = 1|
    var exciter, env, output;

    // Create an excitation signal using a burst of noise
    exciter = WhiteNoise.ar(amp) * Decay2.ar(Impulse.ar(0, 0, amp), 0.005, 0.02);

    // Create an envelope for the overall amplitude
    env = EnvGen.ar(Env.perc(0.001, decay), doneAction: Done.freeSelf);

    // Create a bank of resonators using a parallel filter bank
    output = DynKlank.ar(`[
        // Frequency ratios for the glockenspiel bars
        [1, 4.08, 10.7, 18.8, 24.5, 31.2],
        // Amplitudes of the frequency components
        [1, 0.8, 0.6, 0.4, 0.2, 0.1],
        // Decay times for each frequency component
        [1, 0.8, 0.6, 0.4, 0.2, 0.1]
    ], exciter, freq, 0, hardness);

    // Apply the envelope to the output
    output = output * env;

    // Apply panning and output the sound
    output = Pan2.ar(output, pan);
    Out.ar(0, output);
}).add;
//------------------------------------------------------------
// intial state
//------------------------------------------------------------
// Synth(\glockenspiel, [\freq, 880, \amp, 0.2, \decay, 3.5, \pan, -0.5, \hardness, 3.2]);
~init = ~init <> {
m.ptn.postln;
	Pdef(m.ptn,
		Pbind(
			\instrument, \glockenspiel,
			\note, Pseq([-5,0,4,7,-12,4],inf),
			\root, Pseq([0,5,-2,3,-4,1,-5].stutter(16),inf),
			\decay, 3.5,
			\hardness, 3.5,
			// \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).set(\dur,0.2);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.15);
	Pdef(m.ptn).play();
};


//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;


	e.postln;
};



~onHit = {|state|

	// var vel = 100;
	// var note = 60 + m.com.root - 24	;

	// if(state == true,{
	// 	m.midiOut.noteOn(m.midiChannel, note  , vel);
	// },{
	// 	m.midiOut.noteOff(m.midiChannel, note, 0);
	// });
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var oct = m.accelMassFiltered.linlin(0,5,1,4).floor;
	//
	Pdef(m.ptn).set(\dur, 0.5 - m.accelMassFiltered.squared.linlin(0,8,0,0.45));
	// Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 2 * m.rrateMassThreshold.reciprocal).reciprocal);
	// Pdef(m.ptn).set(0.2);
	Pdef(m.ptn).set(\octave, 4 + oct);

	if(m.accelMass > 0.03,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).resume();
		});
	},{
		if( Pdef(~model.ptn).isPlaying,{
			Pdef(~model.ptn).pause();
		});
	});

	// ((m.accelMassFiltered * 2.0 * m.rrateMassThreshold.reciprocal).reciprocal).postln;
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
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,5,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};




