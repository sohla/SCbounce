(
SynthDef(\miniMoogModel, { |freq = 440, amp = 0.5, gate = 1, pan = 0,
	attack = 0.003, decay = 0.2, sustain = 0.3, release = 0.3, detune = 0.005,
    osc1Mix = 0.33, osc2Mix = 0.33, osc3Mix = 0.33,
    noiseMix = 0.1, lfoRate = 0.5, lfoAmount = 0.5, filterFreq = 2000, filterRes = 0.5|

    var env, osc1, osc2, osc3, noise, filter, lfo, modulatedSignal, mix;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
    osc1 = Saw.ar(freq) * osc1Mix;
	osc2 = Pulse.ar([freq, freq * (1 + detune)], 0.5) * osc2Mix;
	osc3 = SinOsc.ar([freq * (1 - detune), freq]) * osc3Mix;
    noise = WhiteNoise.ar() * noiseMix;
    mix = (osc1 + osc2 + osc3 + noise) * env;
    filter = LPF.ar(mix, filterFreq, filterRes);
    lfo = SinOsc.kr(lfoRate).range(1 - lfoAmount, 1 + lfoAmount);
    modulatedSignal = filter * lfo;
    Out.ar(0, Pan2.ar(modulatedSignal * amp, pan));
}).add;
)


(
Pbind(
    \instrument, \miniMoogModel,
	\note, Pseq([9,2,6,4,8,1,4,8,2,6].stutter(3), inf),
	\octave, Pseq([2,4,5], inf),
    \amp, Pseq([0.5, 0.7, 0.6, 0.8], inf),
    \gate, 1,
	\dur, 0.175,
    \pan, Pseq([-0.2, 0, 0.2, 0], inf),
    \osc1Mix, Pwhite(0,1),
    \osc2Mix, Pwhite(0,1),
	\osc3Mix, Pwhite(0,1),
    \noiseMix, Pwhite(0,0.1),
	\detune, 0.002,
    \lfoRate, 2,
    \lfoAmount, 1,
	\filterFreq, Pwhite(200,10e3),
	\filterRes, 0.8
).play;
)
