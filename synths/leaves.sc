(
SynthDef(\syntheticLeaf, {
    |out=0, pan=0, amp=0.1, grainDur=0.05, grainRate=20, freq=200,
     filterLFOFreq=5, filterLFOAmp=0.02, filterRQ=1, dustiness=0.2,
	leafType=0, gate=1, subAmp=0.02, subFreq=40, wobbleAmp=0.3, wobbleFreq = 2|

    var sig, env, dust, filterEnv, leafNoise, sub, wobble;

    // Create base sound for granulation
    leafNoise = SelectX.ar(leafType, [
		PinkNoise.ar,  // Softer leaves
        BrownNoise.ar, // More crinkly leaves
        GrayNoise.ar   // Crisp leaves

    ]);
	wobble = LFNoise2.ar(wobbleFreq);
	sub = RLPF.ar(GrayNoise.ar, subFreq + wobble.range(0,30) ,0.3,subAmp);


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
    filterEnv = LFNoise2.kr(filterLFOFreq).range(1.0-filterLFOAmp, 1.0+filterLFOAmp);
    sig = BPF.ar(sig, freq * filterEnv, filterRQ) + sub;

    // Envelope
	env = EnvGen.kr(Env.asr(1.3, 1, 2.3, \welch), gate, doneAction: 2) * wobble.range(1.0-wobbleAmp, 1.0);

    // Output
    Out.ar(out, Pan2.ar(sig * env * amp, pan));
}).add;
)

(
// Or create a single instance with specific settings:
~leaves = Synth(\syntheticLeaf, [
	\amp, 0.5,
	\freq, 400,
	\legato,2,
	\grainDur, 0.1,
	\grainRate, 20,//rrand(40,60),
	\filterRQ, 0.03,
	\dustiness, 0.1,
	\leafType, 0
]);
)

