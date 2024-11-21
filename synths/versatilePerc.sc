(
SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, decay=0.5, clickLevel=0.5, amp=0.5, dist = 5|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch;

    // Pitch envelope
    pitch_contour = Line.kr(1, 0, 0.02);

    // Drum oscillator

	pch = freq * (1 + (pitch_contour * tension));
	drum_osc = SinOsc.ar([pch,pch*1.004], LFNoise2.ar([4,5],10,-10),0.5);

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
	sig = (drum_osc * drum_env) + (click_osc * click_env);
	sig = (sig * dist).tanh.distort;
    // Mix and output
    Out.ar(out, Pan2.ar(sig,0,amp))
}).add;
)
// Example usage:
Synth(\versatilePerc, [\freq, 28.rrand(36).midicps, \tension, 5.1, \decay, 0.4, \clickLevel, 0.4, \amp, 0.8]);
Synth(\versatilePerc, [\freq, 90, \tension, 0.05, \decay, 0.2, \clickLevel, 0.3, \amp, 0.7]);
Synth(\versatilePerc, [\freq, 120, \tension, 0.03, \decay, 0.15, \clickLevel, 0.4, \amp, 0.7]);
Synth(\versatilePerc, [\freq, 180, \tension, 0.02, \decay, 0.1, \clickLevel, 0.6, \amp, 0.6]);



(
Pdef(\b,
	Pbind(
		\instrument, \versatilePerc,
		\amp,0.4,
		\octave, Pxrand([3,4,5], inf),
		\note, Pseq([0,3,5,-2,-4,-4].stutter(10)-2, inf),
		\tension, Pwhite(0,1,inf),
		\clickLevel, Pwhite(0.07,0.12,inf),
		\decay, Pwhite(0.2,0.4,inf),
		\dist, Pwhite(1,15,inf),
		\dur,  Pseq([1,1,0.5,Rest(0.5),0.5,1,1,0.5,1,1] * 0.3, inf)
	)
);

Pdef(\b).play(quant:[0.15]);

)



