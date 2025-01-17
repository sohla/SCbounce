(
// Define the Juno-6 synth using Ndef
Ndef(\juno6, {
    |
    // Basic parameters
    freq = 440, amp = 0.5,

    // DCO parameters
    sawLevel = 1.0, pulseLevel = 0.0, subLevel = 0.0,
    pulseWidth = 0.5, pwmRate = 0.0, pwmDepth = 0.0,

    // HPF
    hpfFreq = 20,

    // VCF
    filterFreq = 1000, resonance = 0.5,
    envToVCF = 0.0, lfoToVCF = 0.0, keyFollow = 0.0,

    // LFO
    lfoRate = 0.5,

    // Envelopes
    attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.5,
    filterAttack = 0.1, filterDecay = 0.2, filterSustain = 0.7, filterRelease = 0.5,

    // Chorus
    chorusMix = 0.5  // 0 = off, 0.5 = I, 1.0 = II
    |

    var sig, env, filterEnv, lfo, vcf, chorus1, chorus2;

    // LFO
    lfo = SinOsc.kr(lfoRate);

    // Envelopes
    env = EnvGen.kr(
        Env.adsr(attack, decay, sustain, release),
        gate: 1
    );

    filterEnv = EnvGen.kr(
        Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease),
        gate: 1
    );

    // DCO
    sig = (
        (Saw.ar(freq) * sawLevel) +
        (Pulse.ar(freq, pulseWidth + (lfo * pwmDepth)) * pulseLevel) +
        (Pulse.ar(freq * 0.5, 0.5) * subLevel)  // Sub oscillator
    );

    // HPF
    sig = HPF.ar(sig, hpfFreq);

    // VCF
    vcf = RLPF.ar(
        sig,
        filterFreq *
        (1 + (filterEnv * envToVCF)) *
        (1 + (lfo * lfoToVCF)) *
        (1 + (keyFollow * (freq/440 - 1))),
        resonance.linexp(0, 1, 1, 0.05)
    );

    // Chorus
    chorus1 = DelayC.ar(
        vcf,
        0.1,
        SinOsc.kr(0.2, 0, 0.002, 0.004)
    );

    chorus2 = DelayC.ar(
        vcf,
        0.1,
        SinOsc.kr(0.7, 0, 0.003, 0.006)
    );

    sig = SelectX.ar(
        chorusMix * 2,  // Scale to 0-2 range
        [
            vcf,                    // No chorus
            vcf + chorus1,          // Chorus I
            vcf + chorus1 + chorus2 // Chorus II
        ]
    );

    // Final output
    sig = sig * env * amp;
    sig ! 2  // Stereo output
});
)

// Create the GUI
(
~junoGui = NdefGui(Ndef(\juno6), 32);  // 32 controls

// Set specs for better control ranges
Spec.add(\sawLevel, [0, 1]);
Spec.add(\pulseLevel, [0, 1]);
Spec.add(\subLevel, [0, 1]);
Spec.add(\pulseWidth, [0, 1]);
Spec.add(\pwmRate, [0, 10]);
Spec.add(\pwmDepth, [0, 1]);
Spec.add(\hpfFreq, [20, 400, \exp]);
Spec.add(\filterFreq, [20, 20000, \exp]);
Spec.add(\resonance, [0, 1]);
Spec.add(\envToVCF, [0, 1]);
Spec.add(\lfoToVCF, [0, 1]);
Spec.add(\keyFollow, [0, 1]);
Spec.add(\lfoRate, [0.1, 10, \exp]);
Spec.add(\attack, [0.01, 2, \exp]);
Spec.add(\decay, [0.01, 2, \exp]);
Spec.add(\sustain, [0, 1]);
Spec.add(\release, [0.01, 4, \exp]);
Spec.add(\chorusMix, [0, 2]);

// Set initial values
Ndef(\juno6).set(
    \sawLevel, 1,
    \pulseLevel, 0,
    \subLevel, 0,
    \filterFreq, 1000,
    \resonance, 0.5,
    \attack, 0.1,
    \decay, 0.2,
    \sustain, 0.7,
    \release, 0.5,
    \chorusMix, 0
);
)

// Test pattern
(
Ndef(\juno6).set(\gate, 1);
Pdef(\junoPattern,
    Pbind(
        \type, \set,
        \id, Ndef(\juno6).nodeID,
        \args, #[\freq],
        \dur, Pseq([0.25, 0.25, 0.5, 0.25, 0.25, 0.5], inf),
        \freq, Pseq([440, 523.25, 659.25, 587.33, 523.25, 440], inf)
    )
).play;
)

// Stop pattern
Pdef(\junoPattern).stop;