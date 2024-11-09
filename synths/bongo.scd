(
SynthDef(\bongo, {
    arg out=0, freq=200, amp=0.5, pan=0,
    tension=0.7,  // affects brightness/pitch bend
    damp=0.3,    // affects decay time
    pos=0.3;     // affects harmonic content

    var exciter, membrane, env, earlyRefs, sound;

    // Exciter (strike impulse with noise)
    exciter = Impulse.ar(0) * PinkNoise.ar(1);

    // Membrane simulation using resonant filters
    membrane = DynKlank.ar(
        `[
            // Frequencies are harmonically related but stretched
            [freq, freq*1.7, freq*2.3, freq*2.9],
            // Amplitudes decrease for higher modes
            [1, 0.6, 0.3, 0.2] * pos.squared,
            // Decay times shorter for higher modes
            [damp*0.3, damp*0.2, damp*0.15, damp*0.1]
        ],
        exciter
    );

    // Add initial pitch bend for attack
    membrane = membrane + (
        SinOsc.ar(freq * Line.kr(1.5, 1, 0.02))
        * EnvGen.kr(Env.perc(0.001, 0.09))
        * tension
    );

    // Basic envelope
    env = EnvGen.kr(
        Env.perc(0.001, damp ),
        doneAction: 2
    );

    // Body filter for overall tone
    sound = BPF.ar(
        membrane,
        freq * [1, 2.1],
        0.3
    ).sum;

    // Early reflections
    earlyRefs = DelayN.ar(
        sound,
        0.02,
        [0.01, 0.012, 0.013]
    ).sum * 0.3;

    // Room reverb
    sound = FreeVerb.ar(
        sound + earlyRefs,
        mix: 0.2,
        room: 0.2,
        damp: 0.4
    );

    sound = Pan2.ar(sound * env * amp, pan);
    Out.ar(out, sound);
}).add;
)
(
Pbindef(\bongoPatternA,
    \instrument, \bongo,
	\dur, Pseq([0.25,Rest(0.75)], inf),
    \freq, Prand([100], inf),
    \amp, Pseq([0.6, 0.4, 0.5], inf),
    \tension, Pwhite(0.7, 1),
	\damp, Pwhite(0.3,0.7),
    \pos, Pwhite(0.1, 0.9),
    \pan, Pwhite(-0.3, 0.3),
	\latency,0.2  // changing this shifts things
);

Pbindef(\bongoPatternB,
    \instrument, \bongo,
	\dur, Pseq([Rest(0.25), 0.25,Rest(0.5)], inf),
    \freq, Prand([200], inf),
    \amp, Pseq([0.6, 0.4, 0.5], inf),
    \tension, Pwhite(0.7, 1),
	\damp, Pwhite(0.3,0.7),
    \pos, Pwhite(0.1, 0.9),
    \pan, Pwhite(-0.3, 0.3),
	\latency,0.2 + (0.125)// changing this shifts things
);

Pbindef(\bongoPatternC,
    \instrument, \bongo,
	\dur, Pseq([Rest(0.5), 0.25,Rest(0.25)], inf),
	\freq, Prand([400], inf),
    \amp, Pseq([0.6, 0.4, 0.5], inf),
    \tension, Pwhite(0.7, 1),
	\damp, Pwhite(0.3,0.7),
    \pos, Pwhite(0.1, 0.9),
    \pan, Pwhite(-0.3, 0.3)
);

Pbindef(\bongoPatternD,
    \instrument, \bongo,
	\dur, Pseq([Rest(0.75), 0.25], inf),
    \freq, Prand([800], inf),
    \amp, Pseq([0.6, 0.4, 0.5], inf),
    \tension, Pwhite(0.7, 1),
	\damp, Pwhite(0.3,0.7),
    \pos, Pwhite(0.1, 0.9),
    \pan, Pwhite(-0.3, 0.3)
);
)
(
Pbindef(\bongoPatternA).play(quant:[0.25,0.0]);
Pbindef(\bongoPatternB).play(quant:[0.25,0.0]);
Pbindef(\bongoPatternC).play(quant:[0.25,0.0]);
Pbindef(\bongoPatternD).play(quant:[0.25,0.0]);
)
Tempo.default.tempo = 1.0 * 1.0 