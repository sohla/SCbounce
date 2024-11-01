(
SynthDef(\membraneDrum, {
    arg
    // Basic parameters
    out=0, freq=110, amp=0.5, pan=0,
    // Membrane characteristics
    tension=0.5, // Higher for tom, lower for bodhran
    damping=0.3, // How quickly the membrane settles
    position=0.3, // Strike position from center (0) to edge (1)
    // Body and beater
    bodyResonance=0.6, // Amount of shell resonance
    beaterHardness=0.5, // Softer for bodhran, harder for tom
    shellSize=0.7, // Physical size simulation
    // Timing
    attackTime=0.005,
    releaseTime=2.0;

    var exciter, membrane, body, env, output;
    var numModes = 5;
    var baseFreq = freq;

    // Membrane modes frequency ratios (based on physical modeling)
    var modeFreqs = baseFreq * [
        1,  // Fundamental
        1.59, // First overtone
        2.14, // Second overtone
        2.41, // Third overtone
        2.89  // Fourth overtone
    ];

    // Mode amplitudes based on strike position
    var modeAmps = Array.fill(numModes, { |i|
        var amp = 1.0 / (i + 1);
        // Position affects mode amplitudes
        amp * (1 - (position * (i / numModes))) * 0.7 // Reduced overall mode amplitudes
    });

    // Decay times longer for lower tension (bodhran)
    var modeTimes = Array.fill(numModes, { |i|
        var baseTime = (1 - (i * 0.1)) * (1 - tension);
        baseTime = baseTime * damping.linexp(0, 1, 0.5, 4.0);
        baseTime = baseTime.clip(0.1, 8.0);
    });

    // Reduced and rebalanced exciter
    exciter = Mix([
        // Main beater impact (reduced level)
        LPF.ar(
            WhiteNoise.ar * Env.perc(0.001, 0.01).ar * 0.3,
            beaterHardness.linexp(0, 1, 500, 8000)
        ),

        // Membrane initial displacement (reduced level)
        HPF.ar(
			BrownNoise.ar * Env.perc(0.0007, 0.04).ar * 0.2,
            400
        ),

        // Additional "thump" for tom (reduced level and controlled better)
        SinOsc.ar(baseFreq * [0.5, 1]) *
        Env.perc(0.001, 0.1).ar *
        beaterHardness * 0.15
    ]);

    // Membrane resonance using parallel resonators (reduced input gain)
    membrane = DynKlank.ar(
        `[
            modeFreqs,
            modeAmps,
            modeTimes
        ],
        exciter * 0.4  // Reduced input to resonators
    );

    // Body resonance (rebalanced levels)
    body = Mix([
        // Shell resonance
        Klank.ar(
            `[
                baseFreq * [0.8, 1.2, 2.1, 3.3],
                [0.2, 0.15, 0.1, 0.05] * bodyResonance,  // Reduced amplitudes
                [0.1, 0.08, 0.05, 0.03] * shellSize
            ],
            exciter * 0.1  // Reduced exciter into body
        ),

        // Air cavity resonance
        Resonz.ar(
            membrane,
            baseFreq * [0.75, 1.25],
            0.1
        ).sum * bodyResonance * 0.2  // Reduced cavity resonance
    ]);

    // Combine membrane and body (balanced mix)
    output = Mix([
        membrane * 0.1,
        body * 1.5
    ]);

    // Gentler compression for punch
    output = CompanderD.ar(
        output,
        thresh: 0.9
    );

    // Overall envelope
    env = Env.perc(
        attackTime: attackTime,
        releaseTime: releaseTime * damping.linlin(0, 1, 1.5, 0.5),
        curve: -4
    ).ar(doneAction: 2);

    // Final processing
    output = LeakDC.ar(output);
    output = LPF.ar(output, 16000);  // Gentler lowpass
    output = output * env * amp;

    // Soft limiting stage
    output = (output * 0.7).tanh;  // Soft clip at a lower level

    Out.ar(out, Pan2.ar(output, pan));
}).add;
);

// Example settings for different drums
(
// Tom-like settings
~tomSettings = (
    freq: 110,
    tension: 0.7,
    damping: 0.4,
    position: 0.3,
    bodyResonance: 0.6,
    beaterHardness: 0.7,
    shellSize: 0.8,
    releaseTime: 0.8,
    amp: 0.5  // Reduced amplitude
);

// Bodhran-like settings
~bodhranSettings = (
    freq: 90,
    tension: 0.3,
    damping: 0.2,
    position: 0.4,
    bodyResonance: 0.8,
    beaterHardness: 0.3,
    shellSize: 0.9,
    releaseTime: 1.2,
    amp: 0.5  // Reduced amplitude
);
);

// Test different configurations with cleaner levels
(
// Floor Tom
Synth(\membraneDrum, [
    \freq, 40,
    \tension, 0.2,
    \damping, 0.5,
    \bodyResonance, 0.7,
    \beaterHardness, 0.8,
    \amp, 0.4  // Reduced amp
]);
);

(
// Rack Tom
Synth(\membraneDrum, [
    \freq, 120,
    \tension, 0.8,
    \damping, 0.5,
    \bodyResonance, 0.6,
    \beaterHardness, 0.7,
    \amp, 0.4
]);
);

(
// Bodhran center strike
Synth(\membraneDrum, [
    \freq, 90,
    \tension, 0.3,
    \damping, 0.2,
    \position, 0.2,
    \bodyResonance, 0.8,
    \beaterHardness, 0.3,
    \amp, 0.5
]);
);

// Pattern examples with adjusted levels

Pbindef(\tomPattern,\freq,50)



(
Pbindef(\tomPattern,
    \instrument, \membraneDrum,
    \freq, Pseq([50, 100, 90], inf),
    \dur, 0.5,
    \tension, 0.3,
    \damping, 0.3,
    \position, Pwhite(0.2, 0.4),
    \bodyResonance, 0.6,
    \beaterHardness, 0.8,
    \amp, Pwhite(0.5, 0.7),
	\pan, Pwhite(-1,1)
).play(quant:0.1);
);

// Traditional bodhran pattern
(
Pbindef(\bodhranPattern,
    \instrument, \membraneDrum,
	\freq, Pxrand([100,150,200,250], inf),
    \dur, Pseq([0.4, 0.2, 0.2] *0.5, inf),
    \tension, 0.3,
    \damping, Pseq([0.2, 0.3, 0.3], inf),
    \position, Pseq([0.3, 0.6, 0.6], inf),
    \bodyResonance, 0.8,
    \beaterHardness, Pseq([0.3, 0.4, 0.4], inf),
    \amp, Pseq([0.5, 0.35, 0.35], inf)  // Adjusted dynamics
).play(quant:0.1);
)

(
Pbindef(\bodhranPattern2,
    \instrument, \membraneDrum,
	\freq, Pxrand([100,150,200,250] *8, inf),
    \dur, Pseq([0.4, 0.2, 0.2] *0.5, inf),
	\tension, Pwhite(0.7,0.99),
    \damping, 0.01,
    \position, Pseq([0.3, 0.6, 0.6], inf),
	\bodyResonance, Pwhite(0.01,0.3),
	\beaterHardness, Pwhite(0.01,0.3),
    \amp, Pseq([0.5, 0.35, 0.35] * 2, inf),
	\pan, Pwhite(-0.5,0.5)
).play(quant:0.1);
)

