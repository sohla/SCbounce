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
Synth(\woodBamboo, [\freq, 400, \ringTime, 0.5, \ringMix, 0.9, \noiseMix, 0.1, \amp, 0.5]);