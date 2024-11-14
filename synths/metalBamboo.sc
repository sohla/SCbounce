(
SynthDef(\bambooComplex, {
    arg out=0, freq=440, pan=0, amp=0.5,
        att=0.001, rel=3.0,
        strikePos=0.3, // Position of strike (affects resonance)
        resonance=0.7, // Amount of resonant body sound
        bambooMoisture=0.5, // Affects damping and resonance
        model=0, // Model selector
        width=0.8; // Stereo width

    var exciter, klank, env, noiseSig, bodyResonance;
    var freqs, amps, times;
    var output, numResonators=5;

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
    bodyResonance = bodyResonance + (
        DynKlank.ar(
            `[
                freqs * [1, 1.01],  // Slight detuning
                amps * 0.1,
                times * 1.2
            ],
            exciter * 0.3
        ).dup
    );

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
    output = LeakDC.ar(output);
    output = Limiter.ar(output, 0.95);

    Out.ar(out, output);
}).add;
);

// Example patterns with more bamboo-like settings
(
Pbindef(\bambooPattern,
    \instrument, \bambooComplex,
    \scale, Scale.major,
    \degree, Pseq([0, 2, 4, 7, 9, 7, 4, 2], inf),
    \octave, Prand([2,3,4, 5], inf),
    \dur, Pwhite(0.1, 0.3),
    \amp, Pwhite(0.4, 0.6),
    \pan, Pwhite(-0.6, 0.6),
    \model, Prand([0, 1, 2,3,4,5,6], inf),
    \strikePos, Pwhite(0.1, 0.9),
    \resonance, Pwhite(0.1, 0.9),
	\bambooMoisture, Pwhite(0.1,0.9),
    \rel, Pkey(\dur) * 3
).play;
);

// Different bamboo characteristics
(
// Large, resonant bamboo
Synth(\bambooComplex, [
    \freq, 220,
    \model, 1,
    \resonance, 0.8,
    \bambooMoisture, 0.7,
    \rel, 4.0,
    \amp, 0.6
]);
);

(
// Small, dry bamboo
Synth(\bambooComplex, [
    \freq, 440,
    \model, 2,
    \resonance, 0.6,
    \bambooMoisture, 0.3,
    \rel, 2.0,
    \amp, 0.5
]);
);

(
// Multiple bamboo hits in sequence
Routine({
    var baseFreq = 220;

    [0, 4, 7, 9].do { |interval|
        var freq = baseFreq * (interval * 0.1 + 1);
        Synth(\bambooComplex, [
            \freq, freq,
            \model, [0,1,2,3,4,5,6].choose,
            \strikePos, 0.5,//rrand(0.2, 0.4),
            \resonance, 0.5,//rrand(0.6, 0.8),
            \bambooMoisture, 0.01,
            \amp, 0.5
        ]);
        0.2.wait;
    };
}).play;
)


