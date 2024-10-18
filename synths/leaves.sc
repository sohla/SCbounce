(
SynthDef(\syntheticLeaf, {
    |out=0, pan=0, amp=0.1, grainDur=0.05, grainRate=20,
     filterFreq=9000, filterRQ=1, dustiness=0.2, leafType=0, gate=1|

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
    filterEnv = SinOsc.kr(1,pi).range(7000, 11000);
    sig = BPF.ar(sig, filterEnv, filterRQ);

    // Envelope
    env = EnvGen.kr(Env.asr(1.9, 1, 2.3), gate, doneAction: 2);

    // Output
    Out.ar(out, Pan2.ar(sig * env * amp, pan));
}).add;
)

(
Pbindef(\leafPattern,
    \instrument, \syntheticLeaf,
    \dur, Pexprand(0.2, 2, inf),
    \amp, Pgauss(0.1, 0.02, inf).clip(0.05, 0.2),
    \grainDur, Pwhite(0.04, 0.07, inf),
    \grainRate, Pwhite(40, 65, inf),
    \filterRQ, Pwhite(0.8, 1.2, inf),
    \dustiness, Pwhite(0.1, 0.3, inf),
    \leafType, 0//Pwhite(0, 2, inf)  // Randomly select leaf type
).play;
)
