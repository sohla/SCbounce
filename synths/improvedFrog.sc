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
		\dur, Prand([0.1,0.104,0.108,0.0993], inf) * 1,
        \freq, Pwhite(240, 260, inf),
        \filterStartFreq, Pexprand(1000, 2000, inf),
        \filterEndFreq, Pexprand(200, 400, inf),
        \filterDur, Pwhite(0.005, 0.015, inf),
        \distance, Pwhite(0.1, 0.6, inf),
        \pan, Pwhite(-0.5, 0.5, inf),
        \amp, Pexprand(0.9, 1, inf),
        \roomSize, Pwhite(0.3, 0.5, inf),
        \revAmount, Pwhite(0.2, 0.4, inf),
		\atk, Pwhite(0.005,0.01, inf),
		\dcy, Pwhite(0.06,0.23, inf),

).play;
)


Pbindef(\frogPattern, \freq, Pwhite(40, 260, inf));
Pbindef(\frogPattern, \freq, Pwhite(240, 260, inf));
Pbindef(\frogPattern, \filterDur, Pwhite(0.0003, 0.001, inf));
Pbindef(\frogPattern, \filterStartFreq, 4950);
Pbindef(\frogPattern, \filterEndFreq, 540);

