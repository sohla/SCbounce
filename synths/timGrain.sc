(
SynthDef(\granularSynth, {
    arg out=0, buf=0, gate=1, amp=0.5,
        posLfoFreq=0.1, posLfoAmount=0.5, posLfoType=0, posNorm=0,
        density=20, pitch=1, grainDur=0.1,
        attack=0.01, decay=0.1, sustain=0.5, release=1;

    var sig, env, posLfo, grain, trigger;

    // Position LFO
    posLfo = Select.kr(posLfoType, [
        SinOsc.kr(posLfoFreq),
        LFTri.kr(posLfoFreq),
        LFSaw.kr(posLfoFreq),
        LFPulse.kr(posLfoFreq)
    ]);

    // Trigger for grains
    trigger = Dust.kr(density);

    // Create a single grain
    grain = GrainBuf.ar(
        2,
        trigger,
        grainDur,
        buf,
		[1,1.03] * pitch,
		MouseX.kr(0.19,0.33).lag(2).poll,
        2,
        pan: WhiteNoise.kr(0.6)
    );

    // Apply envelope
    env = EnvGen.kr(
        Env.adsr(attack, decay, sustain, release, curve: -4),
        gate,
        doneAction: 2
    );

    // Final signal
    sig = grain * env * amp;

    Out.ar(out, sig);
}).add;

	SynthDef(\mouseX, { |bus| Out.kr(bus, MouseX.kr(0,1.0))}).add;
	SynthDef(\mouseY, { |bus| Out.kr(bus, MouseY.kr(0,1.0))}).add;

)

(
// Load a buffer with audio data
p = ("/Users/soh_la/Downloads/Voice recordings Music in Motion 25June2024/converted/TR laughing2.wav");
b = Buffer.read(s, p);
)
(
	var mx = Bus.control(s,1);
	var my = Bus.control(s,1);
	Synth(\mouseX, [\bus, mx.index]);
	Synth(\mouseY, [\bus, my.index]);

// Create a Synth instance
x = Synth(\granularSynth, [
    \buf, b,
	\posNorm, 0.2,
    \posLfoFreq, 0.1,
    \posLfoAmount, 1,
    \posLfoType, 2,
    \density, 100,
    \pitch, 0.125,
    \grainDur, 0.2,
    \attack, 0.01,
    \decay, 0.1,
    \sustain, 0.5,
    \amp, 0.5
]);
)
//0.11
//0.33
// Later, to stop the synth
x.set(\gate, 0);