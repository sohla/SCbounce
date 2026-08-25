(
SynthDef(\syntheticLeaf, {
    |out=0, pan=0, amp=0.1, grainDur=0.05, grainRate=20,
     filterFreqMin=900, filterFreqMax=9000, filterRQ=1, dustiness=0.2, leafType=0, gate=1|

    var sig, env, dust, filterEnv, leafNoise;

    // Create base sound for granulation
    leafNoise = SelectX.ar(leafType, [
        PinkNoise.ar,  // Softer leaves
        BrownNoise.ar, // More crinkly leaves
        GrayNoise.ar   // Crisp leaves
    ]);

    // Granular synthesis
    sig = GrainIn.ar(
        numChannels: 1,
        trigger: Impulse.ar(grainRate),
        dur: grainDur,
        in: leafNoise,
        pan: LFNoise1.kr(5)
    );

    // Add some dust for additional texture
    dust = Dust.ar(200 * dustiness) * 0.5;
    sig = sig + dust;

    // Moving filter
    filterEnv = SinOsc.kr(0.3,pi).range(filterFreqMin, filterFreqMax);
    sig = BPF.ar(sig, filterEnv, filterRQ);

    // Envelope
    env = EnvGen.kr(Env.asr(1.3, 1, 2.3, \welch), gate, doneAction: 2);

    // Output
    Out.ar(out, Pan2.ar(sig * env * amp, pan));
}).add;
)

(
Pbindef(\leafPattern,
    \instrument, \syntheticLeaf,
    \dur, Pexprand(1.5, 2, inf),
    \amp, 0.9,
	\legato,2,
    \grainDur, Pwhite(0.04, 0.07, inf),
    \grainRate, Pwhite(40, 65, inf),
	\filterFreqMin, Pseq([42,51,59,63,72,77,81,89,92].midicps, inf),
	\filterFreqMax, Pkey(\filterFreqMin),
    \filterRQ, Pwhite(0.02, 0.04, inf),
    \dustiness, Pwhite(0.1, 0.3, inf),
    \leafType, 0,//Pwhite(0, 2, inf)  // Randomly select leaf type
).play;
)
