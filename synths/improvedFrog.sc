(
SynthDef(\improvedFrog, {
    |out=0, freq=100, filterStartFreq=1000, filterEndFreq=200, filterDur=0.1,
     distance=1, pan=0, amp=0.3, roomSize=0.8, revAmount=0.5, gate=1, atk=0.02, dcy=02|

    var sig, env, filteredSig, filterEnv, distanceAmp, reverbSig;

    // Basic frog sound
    env = EnvGen.kr(Env.perc(atk, dcy, curve: -4));
    sig = LPF.ar(WhiteNoise.ar(0.3) + SinOsc.ar(freq, 0, 2).tanh, 9000);
    sig = sig * env;

    // Envelope filter for timbre shaping
    filterEnv = EnvGen.kr(
        Env([filterStartFreq, filterEndFreq], [filterDur], \exp)
    );
	filteredSig = RLPF.ar(sig, filterEnv, 0.5);

    // Simulate distance
    distanceAmp = (1 - distance).squared;  // Inverse square law for amplitude
    filteredSig = LPF.ar(filteredSig, 20000 * (1 - distance) + 500);  // Distance-based lowpass filter

    // Apply reverb
    reverbSig = FreeVerb.ar(filteredSig, 1, roomSize, 0.9);

    // Mix dry and wet signals
    sig = (filteredSig * (1 - revAmount)) + (reverbSig * revAmount);

    // Final output with distance-based amplitude and panning
    sig = Pan2.ar(sig * distanceAmp * amp, pan);

    // Release envelope to keep the synth running for reverb tail
    sig = sig * EnvGen.kr(Env.asr(0, 1, 3), gate);

    Out.ar(out, sig);

    // Use DetectSilence to free the synth
    DetectSilence.ar(sig, amp: 0.0001, time: 0.5, doneAction: 2);
}).add;
)
(
Pbindef(\frogPattern,
        \instrument, \improvedFrog,
        \dur, 0.1 * 1,//Pexprand(0.1, 0.2, inf),  // Long pauses between calls
        \freq, Pwhite(40, 260, inf),  // Random base frequency
        \filterStartFreq, Pexprand(1000, 2000, inf),  // Start frequency of envelope filter
        \filterEndFreq, Pexprand(200, 400, inf),  // End frequency of envelope filter
        \filterDur, Pwhite(0.005, 0.015, inf),  // Duration of filter envelope
        \distance, Pwhite(0.1, 0.6, inf),  // Random distance (0.1 = close, 1.0 = far)
        \pan, Pwhite(-0.5, 0.5, inf),  // Random panning
        \amp, Pexprand(0.9, 1, inf),  // Random amplitude
        \roomSize, Pwhite(0.3, 0.5, inf),  // Random room size for reverb
        \revAmount, Pwhite(0.2, 0.4, inf),  // Random reverb amount
		\atk, Pwhite(0.005,0.01, inf),
		\dcy, Pwhite(0.06,0.23, inf),

).play;
)


(
Pbindef(\frogPattern,
        \freq, Pwhite(52, 230, inf)
);
)


