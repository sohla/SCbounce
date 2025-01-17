(
SynthDef(\superSynth, {
    |
    // Basic parameters
    freq = 440, amp = 0.5, gate = 1,

    // Envelope parameters
    attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3,

    // Oscillator mix parameters
    sawLevel = 1.0, sineLevel = 0.5, triLevel = 0.5, pulseLevel = 0.3,
    pulseWidth = 0.5,

    // Detuning parameters
    detune1 = 1.001, detune2 = 0.999,

    // Filter parameters
    filterType = 0, // 0 = RLPF, 1 = RHPF, 2 = BPF
    filterFreq = 2000,
    filterRes = 0.5,
    filterEnvAmount = 0.3,
    filterAttack = 0.1, filterDecay = 0.2,
    filterSustain = 0.5, filterRelease = 0.3,

    // LFO parameters
    lfoRate = 6, lfoDepth = 0.0,
    lfoWaveform = 0, // 0 = sine, 1 = triangle, 2 = square
    lfoDestination = 0, // 0 = pitch, 1 = filter, 2 = amplitude

    // Effects parameters
    distAmount = 0.0,
    delayTime = 0.25, delayFeedback = 0.3, delayMix = 0.0,

    // Spatial parameters
    pan = 0, width = 0.5
    |

    var sig, env, filterEnv, lfo, filteredSig, delaySig;

    // Main envelope
    env = EnvGen.kr(
        Env.adsr(attack, decay, sustain, release),
        gate,
        doneAction: 2
    );

    // Filter envelope
    filterEnv = EnvGen.kr(
        Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease),
        gate
    );

    // LFO
    lfo = Select.kr(lfoWaveform, [
        SinOsc.kr(lfoRate),
        LFTri.kr(lfoRate),
        LFPulse.kr(lfoRate)
    ]) * lfoDepth;

    // Frequency modulation from LFO
    freq = freq * (1 + (lfo * (lfoDestination == 0).asInteger));

    // Oscillator mix
    sig = (
        (Saw.ar([freq * detune1, freq * detune2]) * sawLevel) +
        (SinOsc.ar([freq * detune1, freq * detune2]) * sineLevel) +
        (LFTri.ar([freq * detune1, freq * detune2]) * triLevel) +
        (Pulse.ar([freq * detune1, freq * detune2], pulseWidth) * pulseLevel)
    );

    // Filter modulation from LFO
	filterFreq = filterFreq * (1 + (lfo * (lfoDestination == 1).asInteger));

    // Filter section with envelope
    filteredSig = Select.ar(filterType, [
        RLPF.ar(sig, filterFreq * (1 + (filterEnv * filterEnvAmount)), filterRes),
        RHPF.ar(sig, filterFreq * (1 + (filterEnv * filterEnvAmount)), filterRes),
        BPF.ar(sig, filterFreq * (1 + (filterEnv * filterEnvAmount)), filterRes)
    ]);

    // Distortion
    sig = (filteredSig * (1 + distAmount)).tanh;

    // Amplitude modulation from LFO
	sig = sig * (1 + (lfo * (lfoDestination == 2).asInteger));

    // Delay effect
    delaySig = CombL.ar(sig, 1.0, delayTime, delayFeedback);
    sig = (sig * (1 - delayMix)) + (delaySig * delayMix);

    // Apply main envelope and stereo width
    sig = sig * env * amp;
    sig = Balance2.ar(sig[0], sig[1], pan, width);

    Out.ar(0, sig);
}).add;

)

(
// Wobble Bass
Synth(\superSynth, [
    \freq, 55,
    \filterFreq, 800,
    \filterRes, 0.8,
    \lfoRate, 5.5,
    \lfoDepth, 0.7,
    \lfoDestination, 1,
    \sawLevel, 1.0,
    \pulseLevel, 0.6,
    \attack, 0.05,
    \sustain, 0.8,
    \filterEnvAmount, 0.6,
    \distAmount, 0.3
]);
)

(
// Ethereal Pad
Synth(\superSynth, [
    \freq, 440,
    \filterFreq, 2000,
    \lfoRate, 0.2,
    \lfoDepth, 0.3,
    \lfoWaveform, 1,
    \sawLevel, 0.4,
    \sineLevel, 0.8,
    \triLevel, 0.6,
    \attack, 2.0,
    \release, 3.0,
    \delayTime, 0.4,
    \delayMix, 0.3,
    \width, 0.9
]);
)

(
// Plucky Lead
Synth(\superSynth, [
    \freq, 880,
    \filterFreq, 3000,
    \filterRes, 0.4,
    \filterEnvAmount, 0.8,
    \filterAttack, 0.01,
    \filterDecay, 0.1,
    \sawLevel, 0.7,
    \pulseLevel, 0.5,
    \attack, 0.01,
    \decay, 0.1,
    \sustain, 0.3,
    \release, 0.2
]);
)

(
// Modular Drone
Synth(\superSynth, [
    \freq, 110,
    \filterType, 2,
    \filterFreq, 1200,
    \lfoRate, 0.1,
    \lfoDepth, 0.4,
    \lfoDestination, 0,
    \sawLevel, 0.6,
    \sineLevel, 0.7,
    \triLevel, 0.6,
    \attack, 3.0,
    \sustain, 1.0,
    \width, 0.8,
    \delayMix, 0.2
]);
)

(
// Screaming Lead
Synth(\superSynth, [
    \freq, 440,
    \filterFreq, 4000,
    \filterRes, 0.9,
    \filterEnvAmount, 0.7,
    \sawLevel, 1.0,
    \pulseLevel, 0.8,
    \distAmount, 0.6,
    \attack, 0.05,
    \decay, 0.2,
    \sustain, 0.8,
    \lfoRate, 6,
    \lfoDepth, 0.2
]);
)

(
// Cosmic Sweep
Synth(\superSynth, [
    \freq, 220,
    \filterType, 1,
    \filterFreq, 100,
    \filterRes, 0.7,
    \lfoRate, 0.05,
    \lfoDepth, 0.9,
    \lfoDestination, 1,
    \sineLevel, 0.9,
    \triLevel, 0.7,
    \attack, 1.5,
    \release, 4.0,
    \width, 1.0
]);
)

(
// Digital Bell
Synth(\superSynth, [
    \freq, 1760,
    \filterFreq, 5000,
    \filterRes, 0.3,
    \sineLevel, 1.0,
    \triLevel, 0.4,
    \pulseLevel, 0.2,
    \attack, 0.01,
    \decay, 0.5,
    \sustain, 0.3,
    \release, 2.0,
    \delayTime, 0.2,
    \delayMix, 0.15
]);
)

(
// Sub Bass
Synth(\superSynth, [
    \freq, 27.5,
    \filterFreq, 200,
    \filterRes, 0.5,
    \filterEnvAmount, 0.4,
    \sawLevel, 0.6,
    \sineLevel, 1.0,
    \attack, 0.08,
    \decay, 0.3,
    \sustain, 0.9,
    \release, 0.4,
    \distAmount, 0.2
]);
)

(
// Alien Transmission
Synth(\superSynth, [
    \freq, 660,
    \filterType, 2,
    \filterFreq, 2000,
    \filterRes, 0.95,
    \lfoRate, 8,
    \lfoDepth, 0.6,
    \lfoWaveform, 2,
    \lfoDestination, 2,
    \sawLevel, 0.7,
    \pulseLevel, 0.7,
    \attack, 0.2,
    \release, 0.8,
    \delayMix, 0.4
]);
)
(
// Vintage String
Synth(\superSynth, [
    \freq, 440,
    \filterFreq, 1500,
    \filterRes, 0.2,
    \sawLevel, 0.8,
    \sineLevel, 0.4,
    \lfoRate, 4,
    \lfoDepth, 0.1,
    \lfoDestination, 2,
    \attack, 0.3,
    \decay, 0.2,
    \sustain, 0.8,
    \release, 1.0,
    \width, 0.7
]);
)


(
// Hyper Ensemble
Synth(\superSynth, [
    \freq, 220,
    \amp, 0.6,
    \attack, 0.3,
    \decay, 0.2,
    \sustain, 0.8,
    \release, 1.2,
    \sawLevel, 0.7,
    \sineLevel, 0.6,
    \triLevel, 0.5,
    \pulseLevel, 0.4,
    \pulseWidth, 0.7,
    \detune1, 1.0052,
    \detune2, 0.9948,
    \filterType, 0,
    \filterFreq, 2200,
    \filterRes, 0.4,
    \filterEnvAmount, 0.3,
    \filterAttack, 0.2,
    \filterDecay, 0.3,
    \filterSustain, 0.6,
    \filterRelease, 0.8,
    \lfoRate, 5.5,
    \lfoDepth, 0.2,
    \lfoWaveform, 1,
    \lfoDestination, 1,
    \distAmount, 0.1,
    \delayTime, 0.3,
    \delayFeedback, 0.4,
    \delayMix, 0.2,
    \pan, 0.2,
    \width, 0.8
]);
)

(
// Quantum Drift
Synth(\superSynth, [
    \freq, 110,
    \amp, 0.7,
    \attack, 1.5,
    \decay, 0.4,
    \sustain, 0.9,
    \release, 2.0,
    \sawLevel, 0.8,
    \sineLevel, 0.9,
    \triLevel, 0.6,
    \pulseLevel, 0.3,
    \pulseWidth, 0.3,
    \detune1, 1.008,
    \detune2, 0.992,
    \filterType, 2,
    \filterFreq, 1800,
    \filterRes, 0.7,
    \filterEnvAmount, 0.5,
    \filterAttack, 0.8,
    \filterDecay, 0.6,
    \filterSustain, 0.7,
    \filterRelease, 1.5,
    \lfoRate, 0.15,
    \lfoDepth, 0.6,
    \lfoWaveform, 0,
    \lfoDestination, 0,
    \distAmount, 0.15,
    \delayTime, 0.5,
    \delayFeedback, 0.6,
    \delayMix, 0.3,
    \pan, -0.3,
    \width, 0.9
]);
)

(
// Micro Steps
Synth(\superSynth, [
    \freq, 880,
    \amp, 0.5,
    \attack, 0.01,
    \decay, 0.15,
    \sustain, 0.4,
    \release, 0.2,
    \sawLevel, 0.6,
    \sineLevel, 0.4,
    \triLevel, 0.3,
    \pulseLevel, 0.7,
    \pulseWidth, 0.2,
    \detune1, 1.003,
    \detune2, 0.997,
    \filterType, 1,
    \filterFreq, 3000,
    \filterRes, 0.8,
    \filterEnvAmount, 0.7,
    \filterAttack, 0.01,
    \filterDecay, 0.1,
    \filterSustain, 0.3,
    \filterRelease, 0.15,
    \lfoRate, 12,
    \lfoDepth, 0.3,
    \lfoWaveform, 2,
    \lfoDestination, 2,
    \distAmount, 0.25,
    \delayTime, 0.16,
    \delayFeedback, 0.3,
    \delayMix, 0.15,
    \pan, 0.4,
    \width, 0.6
]);
)

(
// Deep Core
Synth(\superSynth, [
    \freq, 55,
    \amp, 0.8,
    \attack, 0.08,
    \decay, 0.3,
    \sustain, 0.7,
    \release, 0.4,
    \sawLevel, 0.9,
    \sineLevel, 1.0,
    \triLevel, 0.4,
    \pulseLevel, 0.6,
    \pulseWidth, 0.6,
    \detune1, 1.002,
    \detune2, 0.998,
    \filterType, 0,
    \filterFreq, 400,
    \filterRes, 0.6,
    \filterEnvAmount, 0.6,
    \filterAttack, 0.05,
    \filterDecay, 0.2,
    \filterSustain, 0.5,
    \filterRelease, 0.3,
    \lfoRate, 4.5,
    \lfoDepth, 0.4,
    \lfoWaveform, 1,
    \lfoDestination, 1,
    \distAmount, 0.4,
    \delayTime, 0.25,
    \delayFeedback, 0.2,
    \delayMix, 0.1,
    \pan, 0.0,
    \width, 0.7
]);
)

(
// Crystal Rain
Synth(\superSynth, [
    \freq, 1760,
    \amp, 0.45,
    \attack, 0.02,
    \decay, 0.8,
    \sustain, 0.3,
    \release, 2.5,
    \sawLevel, 0.3,
    \sineLevel, 0.8,
    \triLevel, 0.7,
    \pulseLevel, 0.2,
    \pulseWidth, 0.8,
    \detune1, 1.006,
    \detune2, 0.994,
    \filterType, 2,
    \filterFreq, 4000,
    \filterRes, 0.3,
    \filterEnvAmount, 0.4,
    \filterAttack, 0.03,
    \filterDecay, 0.6,
    \filterSustain, 0.4,
    \filterRelease, 1.8,
    \lfoRate, 6.5,
    \lfoDepth, 0.15,
    \lfoWaveform, 0,
    \lfoDestination, 2,
    \distAmount, 0.05,
    \delayTime, 0.33,
    \delayFeedback, 0.7,
    \delayMix, 0.4,
    \pan, -0.2,
    \width, 1.0
]);
)

(
// Vapor Wave
Synth(\superSynth, [
    \freq, 440,
    \amp, 0.6,
    \attack, 0.4,
    \decay, 0.3,
    \sustain, 0.8,
    \release, 1.0,
    \sawLevel, 0.7,
    \sineLevel, 0.5,
    \triLevel, 0.6,
    \pulseLevel, 0.4,
    \pulseWidth, 0.4,
    \detune1, 1.01,
    \detune2, 0.99,
    \filterType, 0,
    \filterFreq, 1200,
    \filterRes, 0.5,
    \filterEnvAmount, 0.4,
    \filterAttack, 0.3,
    \filterDecay, 0.4,
    \filterSustain, 0.6,
    \filterRelease, 0.8,
    \lfoRate, 0.8,
    \lfoDepth, 0.3,
    \lfoWaveform, 1,
    \lfoDestination, 1,
    \distAmount, 0.2,
    \delayTime, 0.375,
    \delayFeedback, 0.5,
    \delayMix, 0.3,
    \pan, 0.3,
    \width, 0.8
]);
)

(
// Neural Network
Synth(\superSynth, [
    \freq, 330,
    \amp, 0.55,
    \attack, 0.15,
    \decay, 0.25,
    \sustain, 0.6,
    \release, 0.8,
    \sawLevel, 0.6,
    \sineLevel, 0.7,
    \triLevel, 0.5,
    \pulseLevel, 0.4,
    \pulseWidth, 0.5,
    \detune1, 1.004,
    \detune2, 0.996,
    \filterType, 1,
    \filterFreq, 2600,
    \filterRes, 0.65,
    \filterEnvAmount, 0.45,
    \filterAttack, 0.2,
    \filterDecay, 0.3,
    \filterSustain, 0.5,
    \filterRelease, 0.6,
    \lfoRate, 3.5,
    \lfoDepth, 0.25,
    \lfoWaveform, 2,
    \lfoDestination, 0,
    \distAmount, 0.3,
    \delayTime, 0.2,
    \delayFeedback, 0.4,
    \delayMix, 0.25,
    \pan, -0.4,
    \width, 0.75
]);
)

(
// Quantum Field
Synth(\superSynth, [
    \freq, 110,
    \amp, 0.7,
    \attack, 2.0,
    \decay, 0.5,
    \sustain, 0.9,
    \release, 3.0,
    \sawLevel, 0.5,
    \sineLevel, 0.8,
    \triLevel, 0.6,
    \pulseLevel, 0.3,
    \pulseWidth, 0.7,
    \detune1, 1.007,
    \detune2, 0.993,
    \filterType, 2,
    \filterFreq, 1600,
    \filterRes, 0.55,
    \filterEnvAmount, 0.35,
    \filterAttack, 1.5,
    \filterDecay, 0.6,
    \filterSustain, 0.7,
    \filterRelease, 2.0,
    \lfoRate, 0.2,
    \lfoDepth, 0.5,
    \lfoWaveform, 0,
    \lfoDestination, 1,
    \distAmount, 0.15,
    \delayTime, 0.6,
    \delayFeedback, 0.6,
    \delayMix, 0.35,
    \pan, 0.0,
    \width, 0.9
]);
)

(
// Digital Thunder
Synth(\superSynth, [
    \freq, 55,
    \amp, 0.75,
    \attack, 0.05,
    \decay, 0.4,
    \sustain, 0.7,
    \release, 0.6,
    \sawLevel, 1.0,
    \sineLevel, 0.8,
    \triLevel, 0.4,
    \pulseLevel, 0.6,
    \pulseWidth, 0.3,
    \detune1, 1.005,
    \detune2, 0.995,
    \filterType, 0,
    \filterFreq, 500,
    \filterRes, 0.75,
    \filterEnvAmount, 0.7,
    \filterAttack, 0.04,
    \filterDecay, 0.3,
    \filterSustain, 0.4,
    \filterRelease, 0.5,
    \lfoRate, 7.5,
    \lfoDepth, 0.4,
    \lfoWaveform, 1,
    \lfoDestination, 1,
    \distAmount, 0.5,
    \delayTime, 0.125,
    \delayFeedback, 0.3,
    \delayMix, 0.2,
    \pan, 0.2,
    \width, 0.7
]);
)

(
// Solar Wind
Synth(\superSynth, [
    \freq, 220,
    \amp, 0.65,
    \attack, 1.2,
    \decay, 0.4,
    \sustain, 0.8,
    \release, 2.0,
    \sawLevel, 0.6,
    \sineLevel, 0.7,
    \triLevel, 0.8,
    \pulseLevel, 0.3,
    \pulseWidth, 0.6,
    \detune1, 1.009,
    \detune2, 0.991,
    \filterType, 1,
    \filterFreq, 2000,
    \filterRes, 0.45,
    \filterEnvAmount, 0.5,
    \filterAttack, 0.8,
    \filterDecay, 0.5,
    \filterSustain, 0.6,
    \filterRelease, 1.5,
    \lfoRate, 0.3,
    \lfoDepth, 0.45,
    \lfoWaveform, 0,
    \lfoDestination, 0,
    \distAmount, 0.1,
    \delayTime, 0.4,
    \delayFeedback, 0.5,
    \delayMix, 0.3,
    \pan, -0.3,
    \width, 0.85
]);
)