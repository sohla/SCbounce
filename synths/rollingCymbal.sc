(
SynthDef(\rollingCymbal, {
    arg
    // Basic parameters
    out=0, freq=200, amp=0.3, pan=0,
    // Cymbal character
    harmDensity=0.7,  // Amount of upper harmonics
    ringTime=1.0,     // How long the cymbal rings
    modSpeed=3.0,     // Speed of internal modulation
    brightness=0.5,   // High frequency content
    // Rolling parameters
    rollSpeed=10,     // Speed of rolling effect
    rollAmount=0.5,   // Intensity of rolling
    // Space parameters
    roomSize=0.5,     // Size of reverb space
    damping=0.5,      // Reverb HF damping
    mix=0.3;         // Dry/wet mix

    var fundamentals, partials1, partials2, modulator;
    var cymbal, env, roll, verbSignal;

    // First bank of partials - primary harmonics
    fundamentals = freq * [1, 1.483, 1.932, 2.546, 2.825];

    // Second bank of partials - upper harmonics
    partials1 = fundamentals.collect({ |f|
        f * [ 3.478, 4.123, 5.612, 6.567 ] *
        LFNoise1.kr(0.1).range(0.99, 1.01)
    }).flat;

    // Third bank of partials - even higher harmonics
    partials2 = fundamentals.collect({ |f|
        f * [ 7.267, 8.342, 9.654, 10.987 ] *
        LFNoise1.kr(0.1).range(0.99, 1.01)
    }).flat;

    // Modulator for "shimmer"
    modulator = SinOsc.ar(
        modSpeed * LFNoise1.kr(0.2).range(0.8, 1.2)
    );

    // Rolling envelope
    roll = LFNoise2.kr(
        rollSpeed * LFNoise1.kr(0.1).range(0.8, 1.2)
    ).range(0.4, 1) * rollAmount + (1 - rollAmount);

    // Main cymbal sound
    cymbal = Mix([
        // Fundamental bank
        Saw.ar(fundamentals) * 0.3,

        // First partial bank with modulation
        SinOsc.ar(partials1) *
        LFNoise2.kr(modSpeed * [1, 1.1, 1.2, 1.3]).range(0.1, 1) *
        harmDensity * 0.2,

        // Second partial bank with different modulation
        SinOsc.ar(partials2) *
        LFNoise2.kr(modSpeed * [1.4, 1.5, 1.6, 1.7]).range(0.1, 1) *
        harmDensity * brightness * 0.1
    ]);

    // Add ring modulation
    cymbal = cymbal * modulator;

    // Filter based on brightness
    cymbal = RHPF.ar(
        cymbal,
        brightness.linexp(0, 1, 2000, 8000),
        0.3
    );

    // Main envelope
    env = Env.new(
        levels: [0, 1, 0.3, 0],
        times: [0.002, 0.1, ringTime * 4],
        curve: [-2, -2, -4]
    ).ar(doneAction: 0);

    // Apply rolling modulation
    cymbal = cymbal * roll;

    // Basic shaping
    cymbal = cymbal.tanh;
    cymbal = LeakDC.ar(cymbal);

    // Create stereo field
    cymbal = Splay.ar(cymbal, 0.7);

    // Apply envelope and level
    cymbal = cymbal * env * amp;

    // Reverb
    verbSignal = FreeVerb2.ar(
        cymbal[0],
        cymbal[1],
        mix,
        roomSize,
        damping
    );
	DetectSilence.ar(verbSignal, doneAction:2);
    Out.ar(out, verbSignal);
}).add;
);

// Example patterns and variations
(
// Basic rolling cymbal
Synth(\rollingCymbal, [
    \freq, 200,
    \harmDensity, 0.7,
    \ringTime, 2.0,
    \rollSpeed, 10,
    \rollAmount, 0.6,
    \brightness, 0.6,
    \roomSize, 0.6,
    \mix, 0.3,
    \amp, 0.3
]);
);

(
// Bright crash with fast roll
Synth(\rollingCymbal, [
    \freq, 250,
    \harmDensity, 90,
    \ringTime, 0.7,
    \rollSpeed, 0.1,
    \rollAmount, 100,
    \brightness, 0.9,
    \roomSize, 0.7,
    \mix, 0.4,
    \amp, 0.3
]);
);

(
// Dark ride-like cymbal
Synth(\rollingCymbal, [
    \freq, 180,
    \harmDensity, 0.5,
    \ringTime, 1.5,
    \rollSpeed, 8,
    \rollAmount, 0.4,
    \brightness, 0.4,
    \roomSize, 0.4,
    \mix, 0.2,
    \amp, 0.3
]);
);

// Complex rolling pattern
(
Pdef(\rollingPattern,
    Pbind(
        \instrument, \rollingCymbal,
        \dur, Pseq([2.0, 1.5, 2.5] * 0.25, inf),
        \freq, Prand([1600, 200, 400,800], inf),
        \harmDensity, Pseg(Pseq([0.4, 0.8], inf), Pseq([4, 4], inf), \lin, inf),
        \rollSpeed, Pseg(Pseq([8, 15], inf), Pseq([6, 6], inf), \lin, inf),
        \rollAmount, Pseg(Pseq([0.3, 0.7], inf), Pseq([5, 5], inf), \lin, inf),
        \brightness, Pseg(Pseq([0.4, 0.7], inf), Pseq([4, 4], inf), \lin, inf),
        \roomSize, 0.6,
        \mix, 0.5,
        \amp, 0.9
    )
).play;
);

// Crescendo roll
(
Routine({
    var syn = Synth(\rollingCymbal, [
        \freq, 200,
        \harmDensity, 0.6,
        \ringTime, 4.0,
        \rollSpeed, 8,
        \rollAmount, 0.3,
        \brightness, 0.5,
        \roomSize, 0.6,
        \mix, 0.3,
        \amp, 0.2
    ]);

    20.do {|i|
        syn.set(
            \rollSpeed, 8 + (i * 0.5),
            \rollAmount, 0.3 + (i * 0.03),
            \brightness, 0.5 + (i * 0.02),
            \amp, 0.2 + (i * 0.01)
        );
        0.1.wait;
    };
}).play;
)