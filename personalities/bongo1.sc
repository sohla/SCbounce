var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.99;


SynthDef(\bongo1, {
    arg out=0, freq=200, amp=0.5, pan=0,
    tension=0.7,  // affects brightness/pitch bend
    damp=0.3,    // affects decay time
    pos=0.3;     // affects harmonic content

    var exciter, membrane, env, earlyRefs, sound;

    // Exciter (strike impulse with noise)
    exciter = Impulse.ar(0) * PinkNoise.ar(1);

    // Membrane simulation using resonant filters
    membrane = DynKlank.ar(
        `[
            // Frequencies are harmonically related but stretched
            [freq, freq*1.7, freq*2.3, freq*2.9],
            // Amplitudes decrease for higher modes
            [1, 0.6, 0.3, 0.2] * pos.squared,
            // Decay times shorter for higher modes
            [damp*0.3, damp*0.2, damp*0.15, damp*0.1]
        ],
        exciter
    );

    // Add initial pitch bend for attack
    membrane = membrane + (
        SinOsc.ar(freq * Line.kr(1.5, 1, 0.02))
        * EnvGen.kr(Env.perc(0.001, 0.09))
        * tension
    );

    // Basic envelope
    env = EnvGen.kr(
        Env.perc(0.001, damp ),
        doneAction: 2
    );

    // Body filter for overall tone
    sound = BPF.ar(
        membrane,
        freq * [1, 2.1],
        0.3
    ).sum;

    // Early reflections
    earlyRefs = DelayN.ar(
        sound,
        0.02,
        [0.01, 0.012, 0.013]
    ).sum * 0.3;

    // Room reverb
    sound = FreeVerb.ar(
        sound + earlyRefs,
        mix: 0.2,
        room: 0.2,
        damp: 0.4
    );

    sound = Pan2.ar(sound * env * amp, pan);
    Out.ar(out, sound);
}).add;

~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
		    \instrument, \bongo1,
			\dur, Pseq([Pseq([Rest(0.25), 0.25], 5) ,0.125,0.125, 0.25]* 0.8, inf),
			\octave, 4,
		    \note, Pseq([0,5,9,2,7,4,3], inf),
			\amp, Pseq([0.9, 0.6, 0.8], inf),
		    \tension, Pwhite(0.7, 1),
			\damp, Pwhite(0.5,2),
		    // \pos, Pwhite(0.1, 0.9),
		    \pan, Pwhite(-0.3, 0.3),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);

	Pdef(m.ptn).play(quant:0.125);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};
//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	if(e.root != m.com.root,{
		// "key change".postln;
		Pdef(m.ptn).reset;
	});
	Pdef(m.ptn).set(\root, m.com.root);
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linexp(0,1.5,0.001,2);
	// var swing= m.accelMassFiltered.linexp(0,2.0,0.00001,0.125);
	Pdef(m.ptn).set(\pos, dur);
	// Pdef(m.ptn).set(\latency, 0.2+swing);

	if(m.accelMassFiltered > 0.08,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.125);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
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
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};




