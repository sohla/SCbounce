(
SynthDef(\metalHit, {
    arg out=0, pan=0, amp=0.5,
        attackTime=0.001, decayTime=0.1, sustainLevel=0.3, releaseTime=2,
        freq=500, spread=0.8,
        highPassFreq=200, lowPassFreq=10000,
        nHarmonics=10, irregularity=0.1;

    var exciter, resonator, env, sound;

    // Exciter (initial hit)
    exciter = PinkNoise.ar(1);

    // Resonator (metal body)
    resonator = Klank.ar(
        `[
            // Frequencies
            Array.fill(10, { |i|
                [
					freq * (i + 1) * (1 + (irregularity * (2.0.rand - 1))),
					freq * (i + 1) * (1 + (irregularity * (2.0.rand - 1))) * 0.9
				]
            }),
            // Amplitudes
            Array.fill(10, { |i|
                (nHarmonics - i).reciprocal
            }),
            // Decay times
            Array.fill(10, { |i|
                decayTime * (1 - (i * 0.02))
            })
        ],
        exciter,
        freqscale: 1,
        freqoffset: 0,
        decayscale: 1
    );

    // Apply envelope
    env = EnvGen.kr(
        Env.new(
            [0, 1, sustainLevel, 0],
            [attackTime, decayTime, releaseTime],
            [-4, -2, -4]
        ),
        doneAction: 2
    );

    sound = resonator * env;

    // Apply highpass and lowpass filters
    sound = HPF.ar(sound, highPassFreq);
    sound = LPF.ar(sound, lowPassFreq);

    // Spread the sound in stereo
    sound = Splay.ar(sound, spread);

    // Output
    Out.ar(out, Balance2.ar(sound[0], sound[1], pan, amp));
}).add;
)

(
~metalHit = Synth(\metalHit, [
    \freq, 300,
    \decayTime, 0.3,
    \releaseTime, 0.5,
    \nHarmonics, 15,
    \irregularity, 0.05,
    \highPassFreq, 30,
    \lowPassFreq, 680,
    \amp, 0.7
]);
)

(
var patternDur = 32;  // Total duration of the pattern in beats

p = Pdef(\metalHitPattern,
    Pbind(
        \instrument, \metalHit,
        \dur, Prand([0.25, 0.5, 0.25, 1, 0.5, 0.25, 0.25] * 0.6, inf),
		\octave, 5,
		\note, Prand([0,2,4,5,7,9,11,12], inf),
        \amp, Pexprand(0.3, 0.7),
        \attackTime, Pwhite(0.001, 0.01),
        \decayTime, Pwhite(0.15, 0.3),
        \sustainLevel, 0.1,
        \releaseTime, Pwhite(0.1, 0.5),
        \spread, Pwhite(0.3, 1),
        \highPassFreq, Pwhite(100, 500),
        \lowPassFreq, Pwhite(5000, 15000),
        \nHarmonics, 15,
        \irregularity, 0.1,
        \pan, Pwhite(-0.3, 0.3)
    )
);

// Play the pattern
p.play;

// Stop the pattern after patternDur beats
SystemClock.sched(patternDur, {
    p.stop;
    "Pattern stopped.".postln;
    nil;
});
)