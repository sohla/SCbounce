(
// Warm pad SynthDef with chorus and filter sweep
SynthDef(\warmPad, {
    |out=0, gate=1, freq=440, amp=0.3,
    atk=0.03, dec=0.2, sus=0.8, rel=1.0,
    filtMin=500, filtMax=5000, filtSpeed=0.5,
    detuneAmount = 0.001,chorusRate=0.5, chorusDepth=0.01,
	pan=0, spread=0.2|

    var sig, env, filt, chorus, numVoices=4, sub;

    // Main envelope
    env = EnvGen.kr(
        Env.adsr(atk, dec, sus, rel),
        gate,
        doneAction: 2
    );

    // Multiple slightly detuned oscillators for warmth
    sig = Array.fill(numVoices, { |i|
        var detune = i * detuneAmount;
        var oscillator = SinOsc.ar(freq * (1 + detune)) +
                        Saw.ar(freq * (1 + detune), pi/2 * i) * 0.3;
        Pan2.ar(oscillator, pan + detune)
    }).sum;

    // Filter sweep
    filt = SinOsc.kr(filtSpeed).range(filtMin, filtMax);
    sig = RLPF.ar(sig, filt, 0.5);

    // Chorus effect
    // Anti-aliased chorus using all-pass filter
    chorus = Array.fill(2, {
        var maxDelay = 0.05;
        var delayTime = SinOsc.kr(
            chorusRate + rand(0.1),
            rrand(0, 2pi)
        ).range(0, chorusDepth);

        AllpassC.ar(
            sig,
            maxDelay,
            delayTime + (chorusDepth * 0.1),
            0.1  // Shorter decay time for cleaner sound
        )
    });    // Final processing
	sub = LFTri.ar(freq, pi, 0.2).tanh;
	sig = Mix([sig, chorus.sum]) / (numVoices + 2);
    sig = sig * env * amp;

    // Output with stereo spread
    sig = Splay.ar(sig, spread);

	Out.ar(out, sig + (sub * env));
}).add;
);





(
Pbindef(\warmPadPattern,
    \instrument, \warmPad,
    \dur, Pseq([0.2,0.2,0.2], inf),
	\legato, 0.5,
    \degree, Pseq([0, 11,7], inf),
	\root, Pseq([0,3,2,-2,-5,2].stutter(8), inf),
    \scale, Scale.minor,
    \octave, 3,
    \atk, 0.02,
    \rel, 0.8,
    \filtMin, 200,
    \filtMax, 2000,
    \filtSpeed, 0.001,
    \chorusRate, 0.005,
    \chorusDepth, 0.1,
	\detuneAmount, Pseg(Pseq([0.0001,0.001], inf), 4, \linear),
    \amp, 0.4
).play;
);
