(
SynthDef(\arpThunder, {
    arg
    // Basic parameters
    out=0, gate=1, amp=0.7, freq=52,
    // Filter parameters
    filterMod=0.53, keyFollow=0.5,
    // Envelope timing parameters
    filterRelease=4.65, ampRelease=2.74,
    // Chorus parameters
    chorusRate=0.84, chorusDepth=0.39, chorusMix=0.25,
    // Delay parameters
    delayTime=0.0868, delayFeedback=0.5, delayMix=0.45;

    var sig, filterEnv, ampEnv, filtered, chorused, delayed;
    var nyquist = SampleRate.ir * 0.5;

    // Filter envelope (longer release)
    filterEnv = EnvGen.kr(
        Env.adsr(
            attackTime: 0.001,
            decayTime: 0.3,
            sustainLevel: 0.1,
            releaseTime: filterRelease
        ),
        gate
    );

    // Amplitude envelope (shorter release)
    ampEnv = EnvGen.kr(
        Env.adsr(
            attackTime: 0.001,
            decayTime: 0.1,
            sustainLevel: 1.0,
            releaseTime: ampRelease
        ),
        gate,
        doneAction: 2
    );

    // Noise source
    sig = BrownNoise.ar(1);

    // Key following for filter frequency (optional)
    freq = freq * (keyFollow * (freq / 52)).clip(0.5, 4);

    // 24db/oct filter (cascade two 12db/oct filters for smoothness)
    filtered = RLPF.ar(
        RLPF.ar(
            sig,
            freq: freq * (1 + (filterEnv * filterMod * 1)),
            rq: 0.85  // No resonance as per spec
        ),
        freq: freq * (1 + (filterEnv * filterMod * 8)),
        rq: 0.85
    );

    // Chorus effect
    chorused = Array.fill(2, {
        var lfoPhase = Rand(0, 2pi);
        var delayTime = SinOsc.kr(
            chorusRate,
            lfoPhase,
            chorusDepth * 0.002,  // Scale depth to reasonable delay times
            0.04  // Base delay time
        );
        DelayC.ar(filtered, 0.05, delayTime)
    });

    // Mix chorus
    sig = XFade2.ar(
        filtered,
        Mix(chorused),
        chorusMix * 2 - 1
    );

    // Delay effect with feedback
    delayed = CombL.ar(
        sig,
        maxdelaytime: 1.0,
        delaytime: delayTime,
        decaytime: (delayTime * 8) * delayFeedback
    );

    // Mix delay
    sig = XFade2.ar(
        sig,
        delayed,
        delayMix * 2 - 1
    );

    // Apply amplitude envelope and output
    sig = sig * ampEnv * amp;

    // Soft limiting and DC offset removal
    sig = LeakDC.ar(sig);
    sig = Limiter.ar(sig, 0.95);

    Out.ar(out, sig.dup);
}).add;
);

// Example pattern for random thunder events
(
Pbindef(\thunderPattern,
    \instrument, \arpThunder,
    \dur, Pwhite(1.0, 5.0, inf),  // Random timing between events
    \amp, Pwhite(0.5, 0.7, inf),
    \freq, Pexprand(4, 130, inf),  // Varying base frequency for different thunder characteristics
    \filterMod, 0.9,  // Keep consistent with original spec
    \keyFollow, Pwhite(0.3, 0.7, inf),  // Varying key following for different characters
    \filterRelease, 4.65,
    \ampRelease, 2.74,
    // Slight variations in chorus and delay
    \chorusRate, Pwhite(0.8, 0.9, inf),
    \chorusDepth, 0.39,
    \chorusMix, 0.25,
    \delayTime, 0.0868,
    \delayFeedback, 0.5,
    \delayMix, 0.45
).play;
);

// Single thunder event with exact specified parameters
(
Synth(\arpThunder, [
    \amp, 0.7,
    \freq, 92,
    \filterMod, 0.53,
    \keyFollow, 0.5,
    \filterRelease, 4.65,
    \ampRelease, 2.74,
    \chorusRate, 0.84,
    \chorusDepth, 0.39,
    \chorusMix, 0.25,
    \delayTime, 0.0868,
    \delayFeedback, 0.5,
    \delayMix, 0.45,
]);
)