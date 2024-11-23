var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;


SynthDef(\warmRichSynth, {
    |out=0, freq=440, amp=0.5, gate=1,
        attackTime=0.1, decayTime=0.3, sustainLevel=0.5, releaseTime=1.0,
        cutoff=1000, resonance=0.5,
        detune=0.003, stereoWidth=0.5,
        oscMix=0.5, subOscLevel=0.3,
        filterEnvAmount=0.1, filterAttack=0.03, filterDecay=0.1, filterSustain=0.5, filterRelease=0.5|

    var sig, env, filterEnv, subOsc, stereoSig;

    // ADSR envelope
    env = EnvGen.kr(
        Env.adsr(attackTime, decayTime, sustainLevel, releaseTime),
        gate,
        doneAction: 2
    );

    // Main oscillator (slightly detuned saw waves for richness)
    sig = Mix.ar([
        Saw.ar(freq * (1 - detune)),
        Saw.ar(freq),
        Saw.ar(freq * (1 + detune))
    ]) * (1 - oscMix) ;

    // Add a sine wave oscillator for warmth
    sig = sig + (SinOsc.ar(freq) * oscMix);

    // Sub oscillator for extra depth
    subOsc = SinOsc.ar(freq * 0.5) * subOscLevel;
    sig = sig + subOsc;

    // Stereo widening
    stereoSig = [sig, sig];
    stereoSig = stereoSig + LocalIn.ar(2);
    stereoSig = DelayC.ar(stereoSig, 0.01, SinOsc.kr(0.1, [0, pi]).range(0, 0.01) * stereoWidth);
    LocalOut.ar(stereoSig * 0.5);

    // Filter envelope
    filterEnv = EnvGen.kr(
        Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease),
        gate
    );

    // Apply resonant filter
    sig = RLPF.ar(
        stereoSig,
        cutoff * (1 + (filterEnv * filterEnvAmount)),
        resonance.linexp(0, 1, 1, 0.05)
    );

    // Apply main envelope and output
    sig = sig * env * amp * 0.33;
	Out.ar(out, DelayN.ar(sig,0.01,[0.007,0.009]));
}).add;

~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \warmRichSynth,
			\scale, Scale.major,
			// \octave, Pseq([7,8].stutter(3), inf),
			// \note, Pseq([-5,0,4,-5,0,4,-5,0,4,-5,0,4,-3,2,6,-3,2,6,-3,2,6,-3,2,6,-3,2,6,-3,2,6]-2, inf),
			\octave, Pseq([7,8,6].stutter(3), inf),
			\root,-3,
			\note, Pseq([12,11,7,5,0], inf),
			\legato, 1,
			\attackTime, 0.001,
			\decayTime, 0.2,
			\sustainLevel, 0.1,
			\releaseTime, 1.2,
			\cutoff, Pseg(Pseq([4000,10000], inf), 0.5 * 12, \sine, inf),
			\amp, 0.1,
			// \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);

	Pdef(m.ptn).play(quant:0.5/3);
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

	var dur = m.accelMassFiltered.linlin(0,2.5,0.5/3,0.5/6);
	Pdef(m.ptn).set(\dur, dur);

	// var dur = 0.5 - m.accelMassFiltered.squared.linlin(0,3,0,0.43);
	// Pdef(m.ptn).set(\dur, dur);

	if(m.accelMassFiltered > 0.5,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.5/3);
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




