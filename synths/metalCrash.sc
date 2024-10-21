(

SynthDef(\metalCrash, {
    |out=0, freqBus, ampBus, ringBus, pan=0, amp=0.3, att=0.01, rel=3,
     freqScale=1, decayScale=1, filterFreq=5000, filterQ=0.5,
     noiseAmt=0.5, klankAmp=1, noiseAtt=0.001, noiseRel=0.1|

    var exciter, klank, klank2, noise, noiseSig, sig, env, filterEnv, noiseEnv;
    var freqs, amps, rings;

    // Read from control buses
    freqs = In.kr(freqBus, 8);
    amps = In.kr(ampBus, 8);
    rings = In.kr(ringBus, 8);

    // Exciter (short impulse)
    exciter = Impulse.ar(0);

    // Resonant frequencies (DynKlank)
	klank = Klank.ar(`[freqs, amps, rings], exciter, freqScale, 0, decayScale);
	klank2 = Klank.ar(`[freqs * 1.03, amps, rings], exciter, freqScale, 0, decayScale);

    // Noise component with its own envelope
    noise = PinkNoise.ar(noiseAmt);
    noiseEnv = EnvGen.ar(Env.perc(noiseAtt, noiseRel, curve: -4));
    noiseSig = noise * noiseEnv;

    // Combine Klank and noise
	sig = ([klank ,klank2]* klankAmp) + noiseSig;

    // Main envelope
    env = EnvGen.kr(Env.perc(att, rel), doneAction: 2);

    // Filter envelope
    filterEnv = EnvGen.kr(Env.perc(att, rel * 0.5, curve: -4));
	sig = RLPF.ar(sig, filterFreq * filterEnv, filterQ);

    // Apply main envelope and output
    sig = sig * env * amp * 0.5;
	Out.ar(out, Pan2.ar(sig,pan));
}).add;
)

(
~freqBus = Bus.control(s, 8);
~ampBus = Bus.control(s, 8);
~ringBus = Bus.control(s, 8);
)

    ~freqBus.setn([1, 4, 10.7, 17.2, 24.2, 31.7, 39.1, 48.2] * 200);
    ~ampBus.setn([1, 0.7, 0.5, 0.3, 0.2, 0.1, 0.05, 0.03]);

(
~updateMetalBuses = {
    ~freqBus.setn([1, 3.984, 9.355, 16.69, 24.46, 34.47, 46.25, 60.01]* 200);
    ~ampBus.setn([1, 0.05, 0.02, 0.01, 0.005, 0.003, 0.002, 0.001]);
    ~ringBus.setn({exprand(0.1, 2)}!8);

	~freqBus.getn(8).postln;
};
)

(

Pbindef(\metalCrashPattern,
    \instrument, \metalCrash,
    \freqBus, ~freqBus.index,
    \ampBus, ~ampBus.index,
    \ringBus, ~ringBus.index,
	\dur, Prand([0.1,0.2,0.4],inf),//Pexprand(0.5, 4, inf),  // Random durations between crashes
    \amp, Pexprand(0.4, 0.8, inf),
    \att, Pwhite(0.001, 0.01, inf),
    \rel, Pwhite(2.0, 5.0, inf),
    \freqScale, 0.5,//Pexprand(0.7, 1.3, inf),
    \decayScale, Pwhite(0.8, 1.2, inf),
    \filterFreq, Pexprand(2000, 8000, inf),
    \filterQ, Pwhite(0.3, 0.7, inf),
    \noiseAmt, Pwhite(0.1, 0.3, inf),
    \klankAmp, Pwhite(0.8, 1.2, inf),
    \pan, Pwhite(-0.8, 0.8, inf),
    \noiseAtt, Pwhite(0.0005, 0.002, inf),
    \noiseRel, Pwhite(0.05, 0.07, inf),
    \updateBuses, Pfunc { ~updateMetalBuses.value }
).play;
)

FreqScope.new(1400, 200, 0, server: s);



