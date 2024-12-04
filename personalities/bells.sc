var m = ~model;
var synth;
var lastTime=0;
var notes = [0,8,3,9,5,6,14,2,8,4,9,11,3,6,2] * 6;
var roots = [0,9,8].dupEach(12);
// var notes = [0,1,4,5,7,8,11,12,14] + 24;
// var roots = [0].dupEach(18);
var currentNote = notes[0];
var currentRoot = roots[0];
m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.999;


SynthDef(\bambooComplex, {
    arg out=0, freq=440, pan=0, amp=0.1,
        att=0.001, rel=3.0,
        strikePos=0.1, // Position of strike (affects resonance)
        resonance=0.9, // Amount of resonant body sound
        bambooMoisture=0.1, // Affects damping and resonance
        model=0, // Model selecto
        width=0.8; // Stereo width

    var exciter, klank, env, noiseSig, bodyResonance;
    var freqs, amps, times;
    var output, numResonators=1;

    // Initial strike - gentler, more wooden
    noiseSig = Mix([
        // Main body of the strike
        HPF.ar(PinkNoise.ar, 1000) * 0.6,

        // Bamboo "hollow" characteristic
        BPF.ar(
            BrownNoise.ar,
            freq * [2.7, 4.2],
            0.1
        ).sum * 0.4,

        // High-end detail
        HPF.ar(WhiteNoise.ar, 8000) * 0.1
    ]);

    // Shape the strike
    exciter = noiseSig * Env.perc(att, 0.05).ar(0) * 0.5;

    // Model-specific tunings
    freqs = Select.kr(model, [
        // Original bamboo - emphasized hollow resonances
        freq * [1.0, 2.82, 4.97, 6.15, 8.92],
        // Large bamboo
        freq * [1.0, 2.31, 3.89, 5.12, 7.54],
        // Thin bamboo
        freq * [1.0, 3.12, 5.89, 7.93, 10.12],
        // Short bamboo
        freq * [1.0, 2.92, 4.23, 6.47, 9.32],
        // Wet bamboo (more pronounced lows)
        freq * [1.0, 2.15, 3.89, 5.64, 8.12],
        // Aged bamboo (sparse partials)
        freq * [1.0, 3.35, 5.67, 8.21, 11.42]
    ]);

    // Damping based on bamboo moisture
    times = Array.fill(numResonators, { |i|
        // Lower frequencies decay slower
        var baseTime = (numResonators - i) * 0.8;
        baseTime * bambooMoisture.linlin(0, 1, 0.5, 2.0)
    });

    // Amplitude relationships with strike position influence
    amps = Array.fill(numResonators, { |i|
        var baseAmp = (numResonators - i) / numResonators;
        baseAmp * (1 - (strikePos * (i / numResonators)))
    });

    // Main resonant body - dual Klank for stereo
    bodyResonance = Array.fill(2, {
        Klank.ar(
            `[
                freqs * LFNoise1.kr(0.1!numResonators).range(0.999, 1.001),
                amps,
                times
            ],
            exciter,
            freqscale: 1,
            decayscale: resonance
        )
    });

    // Additional tube resonance
    // bodyResonance = bodyResonance + (
    //     DynKlank.ar(
    //         `[
    //             freqs * [1, 1.01],  // Slight detuning
    //             amps * 0.1,
    //             times * 1.2
    //         ],
    //         exciter * 0.3
    //     ).dup
    // );

    // Overall envelope
    env = EnvGen.kr(
        Env.perc(
            att,
            rel * bambooMoisture.linlin(0, 1, 0.8, 1.2),
            curve: -4
        ),
        doneAction: 2
    );

    // Mix and position in stereo field
    output = Mix([
        Pan2.ar(bodyResonance[0], pan - (width/2)),
        Pan2.ar(bodyResonance[1], pan + (width/2))
    ]);

    // Final shaping
    output = LPF.ar(output, 12000); // Remove any harsh highs
    output = output * env * amp;
    // output = LeakDC.ar(output);
    // output = Limiter.ar(output, 0.95);

    Out.ar(out, output);
}).add;


~init = ~init <> {
};

~deinit = ~deinit <> {
};

//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};
//------------------------------------------------------------
~next = {|d|

	var move = m.accelMassFiltered.linlin(0,3,0,1);
	var att = m.accelMassFiltered.linexp(0,2.5,0.2,0.001);
	var amp = m.accelMassFiltered.linexp(0,2.5,0.08,1);
	var noteIndex = m.accelMassFiltered.linlin(0,2,0.0001,notes.size).floor;
	var space = m.accelMassFiltered.linlin(0,2.5,0.25,0.08);
	if(noteIndex>=notes.size,{noteIndex=notes.size-1});
	if(move > 0.04, {
		if(TempoClock.beats > (lastTime + space),{
			lastTime = TempoClock.beats;
			// notes = notes.rotate(-1);
			currentNote = notes[0];
			roots = roots.rotate(-1);
			currentRoot = roots[0];
			m.com.root = currentRoot;
			synth = Synth(\bambooComplex, [
				\freq, (30 + notes[noteIndex] + currentRoot).midicps,
				\gate, 1,
				\att, att,
				\amp, 0.1 * amp,
				\strikePos, 1.0.rand, // Position of strike (affects resonance)
				\resonance, 0.2, // Amount of resonant body sound
				\bambooMoisture, 1.0.rand, // Affects damping and resonance
				\model, 6.rand.floor, // Model selecto

			]);
			synth.server.sendBundle(0.1,[\n_set, synth.nodeID, \gate, 0]);
		});
	});
};

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
