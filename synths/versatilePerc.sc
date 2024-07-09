(
SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, decay=0.5, clickLevel=0.5, amp=0.5|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env;

    // Pitch envelope
    pitch_contour = Line.kr(1, 0, 0.02);

    // Drum oscillator
    drum_osc = SinOsc.ar(freq * (1 + (pitch_contour * tension)));

    // Click oscillator
    click_osc = LPF.ar(WhiteNoise.ar(1), 1500);

    // Drum envelope
    drum_env = EnvGen.ar(
        Env.perc(attackTime: 0.005, releaseTime: decay, curve: -4),
        doneAction: 2
    );

    // Click envelope
    click_env = EnvGen.ar(
        Env.perc(attackTime: 0.001, releaseTime: 0.01),
        levelScale: clickLevel
    );

    // Mix and output
    Out.ar(out, Pan2.ar(
        (drum_osc * drum_env) + (click_osc * click_env),
        0,
        amp
    ))
}).add;
)
// Example usage:
Synth(\versatilePerc, [\freq, 50, \tension, 0.1, \decay, 0.8, \clickLevel, 0.2, \amp, 0.8]);
Synth(\versatilePerc, [\freq, 90, \tension, 0.05, \decay, 0.2, \clickLevel, 0.3, \amp, 0.7]);
Synth(\versatilePerc, [\freq, 120, \tension, 0.03, \decay, 0.15, \clickLevel, 0.4, \amp, 0.7]);
Synth(\versatilePerc, [\freq, 180, \tension, 0.02, \decay, 0.1, \clickLevel, 0.6, \amp, 0.6]);