(
SynthDef(\advancedCricket, {
    |out=0, freq=4000, chirpSpeed=15, filterFreq=6000, filterQ=0.5,
     delayTime=0.2, delayFeedback=0.3, pan=0, amp=0.1|

    var sig, env, delayedSig, outputSig;

    // Basic cricket chirp
    env = EnvGen.kr(Env.perc(0.001, 0.04), Impulse.kr(chirpSpeed));
    sig = SinOsc.ar(freq) * env;

    // Apply filter
    sig = RLPF.ar(sig, filterFreq, filterQ);

    // Apply amplitude envelope
    sig = sig * EnvGen.kr(Env.linen(0.01, 0.98, 0.18), doneAction: 2);

    // Delay line for spaciousness
    delayedSig = DelayC.ar(sig, 0.5, delayTime);
    sig = sig + (delayedSig * delayFeedback);


    // Stereo spreading
    outputSig = Pan2.ar(sig, pan);

    // Final output
    Out.ar(out, outputSig * amp);
}).add;
)

(1
	Pbindef(\cricketPattern,
        \instrument, \advancedCricket,
		\dur, Pexprand(0.3, 3, inf),  // Pattern runs continuously
        \freq, Pwhite(8000, 11000, inf),  // Random frequency for each cricket
        \chirpSpeed, Pwhite(7, 23, inf),  // Random chirp speed
        \filterFreq, Pexprand(10000, 14000, inf),  // Random filter frequency
        \filterQ, Pwhite(0.3, 0.7, inf),  // Random filter Q
        \delayTime, Pwhite(0.1, 0.3, inf),  // Random delay time
        \delayFeedback, Pwhite(0.2, 0.4, inf),  // Random delay feedback
        \pan, Pwhite(-0.8, 0.8, inf),  // Random panning
        \amp, Pexprand(0.01, 0.1, inf),  // Random amplitude
		\legato, 13
).play;
)
