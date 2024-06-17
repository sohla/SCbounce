var m = ~model;
m.midiChannel = 1;


SynthDef(\funBass, {
    |freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2|
    var osc1, osc2, osc3, env, filter, output;

    // Create an envelope
    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);

    // Create three oscillators with different waveforms and slightly detuned frequencies
    osc1 = Saw.ar(freq);
    osc2 = Pulse.ar(freq * 0.99, 0.6);
    osc3 = SinOsc.ar(freq * 1.01);

    // Mix the oscillators
    output = Mix([osc1, osc2, osc3]) * env * amp;

    // Apply a low-pass filter with resonance
    filter = RLPF.ar(output, filtFreq, filtRes);

    // Apply distortion for added fatness
    filter = (filter * 1.5).tanh;

    // Output the filtered and distorted signal
    Out.ar(0, filter!2);
}).add;
//------------------------------------------------------------
// intial state
//------------------------------------------------------------
// Synth(\glockenspiel, [\freq, 880, \amp, 0.2, \decay, 3.5, \pan, -0.5, \hardness, 3.2]);
~init = ~init <> {
m.ptn.postln;
	Pdef(m.ptn,
		Pbind(
			\instrument, \funBass,
		    \note, Pseq([0,2,7,10,5], inf),
			\octave,Pseq([2,3,4].stutter(2),inf),
			\root, Pseq([0,3,5,-2].stutter(32), inf),
			\envAtk,0.01,
			\envDec,0.2,
			\envSus, 0.0,
			\envRel,Pkey(\octave).squared * 0.05,
			// \dur, 0.1,
    		\amp, 0.4,
    		/*\filtFreq, Pseq([100, 600, 1000,3500], inf),*/
    		\filtRes, 0.7,
			\args, #[],
		)
	);

	// Pdef(m.ptn).set(\dur,0.2);
	// Pdef(m.ptn).set(\octave,5);
	// Pdef(m.ptn).set(\amp,0.15);
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

	var dur = 0.4 * 2.pow(m.accelMassFiltered.linlin(0,4,0,3).floor).reciprocal;
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\filtFreq, m.accelMassFiltered.linlin(0,4,80,4000));

	if(m.accelMass > 0.03,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).play(quant:[0,0,0,0]);
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
	[m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};




