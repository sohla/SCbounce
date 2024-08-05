(
SynthDef(\pipeSynth, {
    arg out=0, gate=1, freq=440,
        attack=0.01, decay=0.1, sustain=0.5, release=0.5,
        metalness=0.5,
        strikePos=0.5,
        damping=0.1,
        pan=0,
        amp=0.5;

    var exciter, resonatorA, resonatorB, env, sound, mix;

    // Exciter (strike)
    exciter = Impulse.ar(0,0,0.1) ;

    // Resonator (pipe body)
    resonatorA = DynKlank.ar(
        `[
            // Frequencies
				[1, 2, 3, 4, 5] * freq,
            // Amplitudes
            [1, 0.7, 0.5, 0.3, 0.1] * (1 - strikePos),
            // Decay times
            [1, 0.9, 0.8, 0.7, 0.6] * (1 - damping)
        ],
        exciter
    );

    resonatorB = DynKlank.ar(
        `[
            // Frequencies
			[1, 2, 3, 4, 5] * (freq + freq * 1.008),
            // Amplitudes
            [1, 0.7, 0.5, 0.3, 0.1] * (1 - strikePos),
            // Decay times
            [1, 0.9, 0.8, 0.7, 0.6] * (1 - damping)
        ],
        exciter
    );
	mix = [resonatorA,resonatorB];
    // Mix between metallic and wooden sound
    sound = XFade2.ar(
        mix,
        CombL.ar(mix, 0.1, 1 / freq, 2),
        (metalness * 2) - 1
    );

    // Apply envelope
    env = EnvGen.kr(
        Env.adsr(attack, decay, sustain, release),
        gate,
        doneAction: 2
    );

    sound = sound * env * amp;

    // Output
    Out.ar(out, Pan2.ar(sound, pan));
}).add;

)

(
~pipe = Synth(\pipeSynth, [
    \freq, 240,
    \metalness, 0.5,
    \strikePos, 0.4,
    \damping, 0.5,
    \attack, 0.002,
    \decay, 0.3,
    \sustain, 0.0,
    \release, 0.1,
    \amp, 0.6
]);
)
// To stop the synth
~pipe.set(\gate, 0);

(
var patternDur = 120;  // Total duration of the pattern in seconds
var tempo = 132/60;    // 132 BPM converted to beats per second

p = Pdef(\pipeGlassPattern,
    Pbind(
        \instrument, \pipeSynth,
        \dur, Pseq([0.25, 0.25, 0.5, 0.25, 0.25, 0.5, 0.5, 0.5], inf) / tempo,
        \freq, Pseq([
            Pseq([440, 550, 660, 550], 4),
            Pseq([495, 618.75, 742.5, 618.75], 4),
            Pseq([412.5, 515.625, 618.75, 515.625], 4)
        ], inf),
        \amp, Pgauss(0.3, 0.05, inf),
        \gate, 1,
        \attack, 0.01,
        \decay, Pseg(Pseq([0.1, 0.3], inf), Pseq([32, 32], inf), \linear, inf) / tempo,
        \sustain, 0.7,
        \release, 0.1,
        \metalness, Pseg(Pseq([0, 1], inf), Pseq([64, 64], inf), \sine, inf),
        \strikePos, Pwhite(0.2, 0.8),
        \damping, Pseg(Pseq([0.05, 0.5], inf), Pseq([48, 48], inf), \exp, inf),
        \pan, Pwhite(-0.5, 0.5)
    )
);

// Play the pattern
p.play;

// Stop the pattern after patternDur seconds
SystemClock.sched(patternDur, {
    p.stop;
    "Pattern stopped.".postln;
    nil;
});
)

(
var patternDur = 60;  // Total duration of the pattern in seconds
var tempo = 60/60;   // 160 BPM converted to beats per second

p = Pdef(\pipeShortNotesPattern,
    Pbind(
        \instrument, \pipeSynth,
        \dur, Pseq([0.125, 0.125, 0.25, 0.125, 0.125, 0.25], inf) / tempo,
        \note, Prand([0, 2, 4, 5, 7, 9, 11], inf),
        \octave, Pwrand([4, 5, 6], [0.9, 0.5, 0.1], inf),
        \amp, Pgauss(0.4, 0.5, inf),
        \attack, 0.005,
        \decay, Pwhite(0.05, 0.2, inf),
        \sustain, 0.3,
        \release, 1.05,
        \metalness, Pseg(Pseq([0.2, 1], inf), Pseq([20, 20], inf), \sine, inf),
        \strikePos, Pseg(Pseq([0.2, 1], inf), Pseq([10, 10], inf), \sine, inf),
        \damping, Pseg(Pseq([0.05, 0.1], inf), Pseq([10, 10], inf), \exp, inf),
        \pan, Pwhite(-0.3, 0.3, inf)
    )
);

// Play the pattern
p.play;

// Stop the pattern after patternDur seconds
SystemClock.sched(patternDur, {
    p.stop;
    "Pattern stopped.".postln;
    nil;
});
)