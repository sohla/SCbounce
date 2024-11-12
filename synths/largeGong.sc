(
SynthDef(\largeGong, {
    arg
    // Basic parameters
    out=0, freq=70, amp=0.5, pan=0,
    // Gong characteristics
    strikeForce=0.7,    // Impact intensity
    shimmerAmount=0.6,  // Amount of characteristic gong wobble
    metallic=0.7,       // Metallic character
    size=0.8,          // Size affect harmonics and decay
    // Time and space
    attackTime=0.002,
    decayTime=12.0,     // Long decay for large gong
    roomSize=0.9,
    damping=0.3,
    mix=0.5;

    var strike, partials, gongSound, shimmer, env, output;
    var numPartials = 12;
    var baseFreq = freq;

    // Complex frequency ratios for large gong
    var ratios = [
        1,      // Fundamental
        1.36,   // Major third overtone
        1.97,   // Dominant overtone
        2.43,   // Characteristic gong partial
        3.24,   // Upper partial
        4.16,   // High partial
        5.61,   // Shimmer frequency 1
        6.15,   // Shimmer frequency 2
        7.20,   // High resonance 1
        8.33,   // High resonance 2
        10.47,  // Upper shimmer 1
        12.65   // Upper shimmer 2
    ];

    // Initial strike sound
    strike = Mix([
        // Metal impact
        HPF.ar(WhiteNoise.ar, 1000) * Env.perc(0.001, 0.01).ar,

        // Low thump
        SinOsc.ar(baseFreq * [0.5, 1]) * Env.perc(0.001, 0.08).ar,

        // Mid frequencies
        BPF.ar(PinkNoise.ar, baseFreq * 2, 0.2) * Env.perc(0.001, 0.05).ar
    ]) * strikeForce * 0.3;

    // Shimmer effect - slow beating frequencies
    shimmer = SinOsc.kr(
        [0.15, 0.17, 0.23, 0.27] * shimmerAmount,
        mul: shimmerAmount * 0.4,
        add: 1
    );

    // Create partial frequencies with individual envelopes
    partials = Mix.fill(numPartials, { |i|
        var env, vol, freq, beating;

        // Each partial gets longer decay time
        env = Env.perc(
            attackTime: attackTime,
            releaseTime: decayTime * (1 + (i * 0.3)) * size,
            level: 1.0 / (i + 1),
            curve: [-2, -4]
        ).ar;

        // Slight random frequency variations
        freq = baseFreq * ratios[i] * LFNoise2.kr(0.1).range(0.999, 1.001);

        // Volume based on partial number and metallic parameter
        vol = (1 / (i + 1)) * (1 - (i * 0.5 * (1 - metallic)));

        // Beating patterns for each partial
        beating = SinOsc.kr(
            shimmerAmount * 0.2 * (i + 1),
            mul: 0.1,
            add: 1
        );

        // Combine oscillators with slight detuning
        Mix([
            SinOsc.ar(freq),
            SinOsc.ar(freq * 1.001),
            SinOsc.ar(freq * 0.999)
        ]) * env * vol * beating
    });

    // Combine strike and partials
    gongSound = (strike * 0.3) + (partials * 0.7);

    // Apply shimmer modulation
    gongSound = gongSound * shimmer;

    // Overall envelope
    env = Env.perc(
        attackTime: attackTime,
        releaseTime: decayTime,
        level: amp,
        curve: [-2, -4]
    ).ar(doneAction: 2);

    // Basic processing
    gongSound = LeakDC.ar(gongSound);
    gongSound = LPF.ar(gongSound, 6000);
    gongSound = HPF.ar(gongSound, 30);

    // Create stereo field
    output = Splay.ar(gongSound, spread: size.linlin(0, 1, 0.3, 0.8));

    // Add space
	// output = FreeVerb2.ar(
	// 	output[0],
	// 	output[1],
	// 	mix,
	// 	roomSize,
	// 	damping
	// );
	output[0]= CombL.ar(output[0], 0.1, [0.0297, 0.0371, 0.0411, 0.0437], 2, roomSize).sum * 0.2;
	output[1] = CombL.ar(output[1], 0.1, [0.0277, 0.0353, 0.0389, 0.0419], 2, roomSize).sum* 0.2;

    // Final shaping
    output = output * env;
    output = Limiter.ar(output, 0.95);

    Out.ar(out, output);
}).add;
);

// Example uses and variations
(
// Large ceremonial gong
Synth(\largeGong, [
    \freq, 80,
    \strikeForce, 0.8,
    \shimmerAmount, 0.7,
    \metallic, 0.7,
    \size, 0.9,
    \decayTime, 15.0,
    \roomSize, 0.9,
    \mix, 0.5,
    \amp, 0.3
]);
);

(
// Smaller, brighter gong
Synth(\largeGong, [
    \freq, 55*3,
    \strikeForce, 0.6,
    \shimmerAmount, 0.5,
    \metallic, 0.8,
    \size, 0.6,
    \decayTime, 18.0,
    \roomSize, 0.7,
    \mix, 0.8,
    \amp, 0.4
]);
);

(
// Huge temple gong
Synth(\largeGong, [
    \freq, 45.midicps,
    \strikeForce, 0.9,
    \shimmerAmount, 0.8,
    \metallic, 0.6,
    \size, 1.0,
    \decayTime, 20.0,
    \roomSize, 0.95,
    \mix, 0.6,
    \amp, 0.3
]);
);

// Gong crescendo
(
Routine({
    var numStrikes = 50;
    var baseTime = 1.0;

    numStrikes.do { |i|
        var force = 0.3 + (i * 0.15);
        var time = baseTime - (i * 0.4);

        Synth(\largeGong, [
            \freq, 55,
            \strikeForce, force,
            \shimmerAmount, 0.5 + (i * 0.1),
            \metallic, 0.9,
            \size, 0.9,
            \decayTime, 1.0,
            \amp, 0.3 + (i * 0.05)
        ]);

        time.wait;
    };
}).play;
)