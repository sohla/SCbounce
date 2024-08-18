(
SynthDef(\femaleVocal, {
    arg out=0, freq=440, amp=0.5, gate=1,
        attackTime=0.1, decayTime=0.1, sustainLevel=0.8, releaseTime=0.3,
        vibRate=5, vibDepth=0.02,
        breathiness=0.2, brightness=0.5, throatiness=0.5;

    var sig, env, vibrato;

    // ADSR envelope
    env = EnvGen.kr(
        Env.adsr(attackTime, decayTime, sustainLevel, releaseTime),
        gate,
        doneAction: 2
    );

    // Vibrato
    vibrato = SinOsc.kr(vibRate).range(1 - vibDepth, 1 + vibDepth);

    // Base oscillator with vibrato
    sig = SawDPW.ar(freq * vibrato);

    // Formant filters for "ahhh" sound
	// sig = BPF.ar(sig, [800, 1150, 2900, 3900, 4950], [0.1, 0.1, 0.1, 0.1, 0.1], [1, 0.5, 0.25, 0.1, 0.1].normalizeSum);
	sig = BPF.ar(sig, ([800, 1150, 2900, 3900, 4950] / 800) * freq, [0.1, 0.1, 0.1, 0.1, 0.1], [1, 0.5, 0.25, 0.1, 0.1].normalizeSum);

    // Add breathiness
    sig = sig + (PinkNoise.ar(breathiness) * LFNoise2.kr(2).range(0.5, 1));

    // Control brightness
    sig = RHPF.ar(sig, brightness.linexp(0, 1, 400, 6000), 0.6);

    // Control throatiness (low-mid emphasis)
    sig = BPeakEQ.ar(sig, throatiness.linexp(0, 1, 400, 800), 0.5, 3);

    // Apply envelope and output
    sig = sig * env * amp;
    Out.ar(out, sig ! 2);
}).add;
)

(
SynthDef(\warmRichSynth, {
    arg out=0, freq=440, amp=0.5, gate=1,
        attackTime=0.1, decayTime=0.3, sustainLevel=0.5, releaseTime=1.0,
        cutoff=1000, resonance=0.5,
        detune=0.1, stereoWidth=0.5,
        oscMix=0.5, subOscLevel=0.3,
        filterEnvAmount=0.1, filterAttack=0.03, filterDecay=0.1, filterSustain=0.5, filterRelease=0.5;

    var sig, env, filterEnv, subOsc, stereoSig;

    // ADSR envelope
    env = EnvGen.kr(
        Env.adsr(attackTime, decayTime, sustainLevel, releaseTime),
        gate,
        doneAction: 2
    );

    // Main oscillator (slightly detuned saw waves for richness)
    sig = Mix.ar([
        Saw.ar(freq * (1 - detune)),
        Saw.ar(freq),
        Saw.ar(freq * (1 + detune))
    ]) * (1 - oscMix) ;

    // Add a sine wave oscillator for warmth
    sig = sig + (SinOsc.ar(freq) * oscMix);

    // Sub oscillator for extra depth
    subOsc = SinOsc.ar(freq * 0.5) * subOscLevel;
    sig = sig + subOsc;

    // Stereo widening
    stereoSig = [sig, sig];
    stereoSig = stereoSig + LocalIn.ar(2);
    stereoSig = DelayC.ar(stereoSig, 0.01, SinOsc.kr(0.1, [0, pi]).range(0, 0.01) * stereoWidth);
    LocalOut.ar(stereoSig * 0.5);

    // Filter envelope
    filterEnv = EnvGen.kr(
        Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease),
        gate
    );

    // Apply resonant filter
    sig = RLPF.ar(
        stereoSig,
        cutoff * (1 + (filterEnv * filterEnvAmount)),
        resonance.linexp(0, 1, 1, 0.05)
    );

    // Apply main envelope and output
    sig = sig * env * amp * 0.33;
	Out.ar(out, DelayN.ar(sig,0.01,[0.007,0.009]));
}).add;
)

(
SynthDef(\orchestralHarp, {
    arg out=0, freq=440, amp=0.5, gate=1,
        attackTime=0.005, decayTime=0.2, sustainLevel=0.5, releaseTime=3.0,
        pluckPos=0.5, stringDamping=0.1, bodyResonance=0.8,
        stereoWidth=0.5, brightness=0.5, lowCut=80;

    var sig, env, pluck, body, stereoSig;

    // ADSR envelope
    env = EnvGen.kr(
        Env.adsr(attackTime, decayTime, sustainLevel, releaseTime),
        gate,
        doneAction: 2
    );

    // Pluck generator using Karplus-Strong algorithm
    pluck = Pluck.ar(
        in: WhiteNoise.ar(0.1),
        trig: Impulse.kr(0),
        maxdelaytime: 1,
        delaytime: 1 / freq,
        decaytime: 30 * (1 - stringDamping),
        coef: pluckPos
    );

    // Add some complexity to the pluck sound
    pluck = pluck + DelayN.ar(pluck, 0.01, 0.01);
    pluck = LPF.ar(pluck, freq * 10 * brightness);

    // Body resonator
    body = DynKlank.ar(
        `[
            [1, 2, 2.8, 3.5] * freq,    // Resonant frequencies
            [1, 0.6, 0.4, 0.2] * bodyResonance,    // Amplitudes
            [1, 0.5, 0.7, 0.3]    // Decay times
        ],
        pluck
    );

    // Mix pluck and body
    sig = XFade2.ar(pluck, body, bodyResonance * 2 - 1);

    // Stereo widening
    stereoSig = Splay.ar([sig, DelayC.ar(sig, 0.01, 0.01)], stereoWidth);

    // High-pass filter to remove unwanted low frequencies
    sig = HPF.ar(stereoSig, lowCut);

    // Apply envelope and output
    sig = sig * env * amp * 0.33;
    Out.ar(out, sig);
}).add;
)

(
x = Synth(\femaleVocal, [
    \out, 0,
    \freq, 340,  // A4 note
    \amp, 0.5,
    \attackTime, 0.1,
    \decayTime, 0.1,
    \sustainLevel, 0.8,
    \releaseTime, 0.3,
    \vibRate, 5,
    \vibDepth, 0.014,
    \breathiness, 0.002,
    \brightness, 0.2,
    \throatiness, 0.9
]);
)
(
// To stop the synth
x.set(\gate, 0);
)


(
Pbind(
    \instrument, \warmRichSynth,
 	\root, Pseq([0,3,6,-3].stutter(12)-2, inf),
    \note, Pseq([4,6,7,6,7,9], inf),
    \dur, Pseq([0.5, 0.5, 1, 0.5, 0.5, 2], inf),
    \legato, 0.8,
	\detune,0.003,
	\cutoff, 5111,
	\subOscLevel,0,
	\attackTime, 0.3,
	\decayTime, 0.5,
	\sustainLevel,0.7,
    \amp, 0.7
).play(quant:0.1);
Pbind(
	\instrument, \warmRichSynth,
	\scale, Scale.major,
	\octave, 7,
	\root, Pseq([0,3,-6,-3].stutter(60)-2, inf),
	\note, Pseq([-5,0,4,-5,0,4,-5,0,4,-5,0,4,-3,2,6,-3,2,6,-3,2,6,-3,2,6,-3,2,6,-3,2,6], inf),
	\dur, Pseq([0.5/3], inf),
	\legato, 1,
	\attackTime, 0.001,
	\decayTime, 0.2,
	\sustainLevel, 0.1,
	\releaseTime, 3,
	\cutoff, Pseg(Pseq([4000,10000], inf), 0.5 * 12, \sine, inf),
	\amp, 0.3
).play(quant:0.1);
Pbind(
	\type, \customEvent,
	\instrument, \stereoSampler,
	\dur, 0.5/3,
	\legato, 3,
	\amp,0.3,
	// \note, Pn(Pseries(60,2, 20), inf),
	// \root, Pseq([0,-1].stutter(20), inf),
	\root, Pseq([0,3,-6,-3].stutter(60)-2, inf),
	\note, Pseq([-5,0,4,-5,0,4,-5,0,4,-5,0,4,-3,2,6,-3,2,6,-3,2,6,-3,2,6,-3,2,6,-3,2,6]+72, inf),
	\release,1,
).play(quant:0.1);
Pbind(
    \instrument, \warmRichSynth,
    \scale, Scale.major,
	\octave, Pseq([4,3],inf),
	\root, Pseq([0,-9,-6,-3].stutter(2)-2, inf),
    \note, Pseq([0], inf),
    \dur, Pseq([5], inf),
    \legato, 1,
	\detune,0.003,
	\oscMix, 0.4,
	\subOscLevel, 0.6,
	\cutoff, 600,
	\resonance, 0.4,
	\attackTime, 1,
    \amp, 0.9
).play(quant:0.1);

)


