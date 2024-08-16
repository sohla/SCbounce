(
SynthDef(\woodBamboo, {
    |out=0, freq=1000, ringTime=0.1, ringMix=0.5, noiseMix=0.5, amp=0.5|
    var exciter, resonator, noiseSig, output;

    // Exciter: short impulse
    exciter = Impulse.ar(0);

    // Resonator: two resonant filters for a more complex tone
    resonator = Klank.ar(
        `[
            [freq, freq*1.5, freq*2.3], // Resonant frequencies
            [1, 0.6, 0.3],             // Amplitudes
            [ringTime, ringTime*0.9, ringTime*0.8]  // Decay times
        ],
        exciter
    );

    // Noise component for the "click" sound
    noiseSig = LPF.ar(WhiteNoise.ar, 7000) * EnvGen.ar(Env.perc(0.001, 0.01));

    // Mix resonator and noise
    output = (resonator * ringMix) + (noiseSig * noiseMix);

    // Apply amplitude envelope
    output = output * EnvGen.ar(Env.perc(0.001, ringTime * 2), doneAction: 2);

    // Output
    Out.ar(out, Pan2.ar(output, 0, amp));
}).add;
)
// Example usage:
Synth(\woodBamboo, [\freq, 1200, \ringTime, 0.1, \ringMix, 0.7, \noiseMix, 0.1, \amp, 0.5]);
Synth(\woodBamboo, [\freq, 800, \ringTime, 0.2, \ringMix, 0.8, \noiseMix, 0.2, \amp, 0.5]);
Synth(\woodBamboo, [\freq, 45.midicps, \ringTime, 1.7, \ringMix, 0.7, \noiseMix, 0.01, \amp, 2]);


(
Pbind(
    \instrument, \woodBamboo,
    \dur, Pseq([0.25, 0.25, 0.5, 0.25, 0.25, 0.5] * 0.5, inf),
    \freq, Prand([200, 250, 300, 350, 400], inf),
    \ringTime, Pwhite(0.05, 0.2),
    \ringMix, Pwhite(0.3, 0.7),
    \noiseMix, Pwhite(0.3, 0.7),
    \amp, Pwhite(0.3, 0.6)
).play;
)


(
Pbind(
    \instrument, \woodBamboo,
    \dur, Pseq([
        Pseq([0.25, 0.25, 0.5, Rest(0.25), 0.25, 0.5], 2),
        Pseq([0.125, 0.125, 0.25, Rest(0.25), 0.25], 2),
        Pseq([1, Rest(0.5), 0.5, Rest(0.25), 0.25, 0.25, 0.25], 1)
    ], inf),
    \freq, Prand([
        Pseq([200, 250, 300]),
        Pseq([350, 400, 450]),
        Prand([500, 550, 600], 3)
    ], inf),
    \ringTime, Pseg(
        Pseq([0.05, 0.2, 0.05], inf),
        Pseq([4, 4], inf),
        \sine
    ),
    \ringMix, Pwhite(0.3, 0.7),
    \noiseMix, Pseg(
        Pseq([0.2, 0.8, 0.2], inf),
        Pseq([8, 8], inf),
        \sine
    ),
    \amp, Pseq([
        Pseq([0.6, 0.4, 0.5, 0.3], 3),
        Pseq([0.2, 0.3, 0.4, 0.5, 0.6], 1)
    ], inf) * Pwhite(0.8, 1.0)
).play;
)



(
Pbind(
	\note,  Pseg( Pseq([1, 5],inf), 4, \linear),
    \dur, 1
).play;
)



