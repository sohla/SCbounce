(SynthDef(\superBassOscMod, {
    arg freq = 30,
        detune = 0.5,
        lfo1Rate = 0.2,
        lfo2Rate = 0.3,
        lfo1Depth = 0.8,
        lfo2Depth = 0.2,
        freqModRate = 4,
        freqModDepth = 100,
        amp = 1,
        pan = -0.7;

    var lfo1, lfo2, freqMod, osc1, osc2, mix;

    // Create all modulation signals
    lfo1 = LFTri.ar(lfo1Rate) * lfo1Depth;
    lfo2 = LFTri.ar(lfo2Rate) * lfo2Depth;
    freqMod = SinOsc.ar(freqModRate) * freqModDepth;


    // Main oscillators
    osc1 = SinOsc.ar(
        freq + freqMod,
        mul: 0.5 * (1 + lfo1)
    );

    osc2 = HPF.ar(SinOsc.ar(
        freq * (1 + detune) + freqMod,
        mul: 0.5 * (1 + lfo2)
    ), 100);

    // Mix and output
    mix = Mix([osc1, osc2] * 4).tanh * amp;
    mix = Pan2.ar(mix, pan);

    Out.ar(0, mix);
}).add;
)
Synth(\superBassOscMod,[\freq, 45.midicps, \detune, 0.004, \lfo1Rate, 4, \lfo1Depth, 0.3, \lfo1Rate, 5, \lfo2Depth, 0.3, \freqModRate, 21.midicps,\freqModDepth, 100, \amp, 0.5])