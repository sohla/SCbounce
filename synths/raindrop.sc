(
SynthDef(\raindrop, {
    |out=0, freq=1000, amp=0.1, pan=0, gate=1, attackTime=0.001, decayTime=0.05,
	filterFreq=3000, filterRQ=1, wobble=10,
 	reverbMix=0.3, reverbRoom=0.03, reverbDamp=0.5|

	var sig, env;
    var verb;

    // Envelope for the droplet shape
    env = EnvGen.ar(Env.perc(attackTime, decayTime), gate);

    // Basic droplet sound
    sig = SinOsc.ar(freq * LFPar.ar(wobble,pi/2, 0.2,1)) * env;

    // Apply bandpass filter
    sig = BPF.ar(sig, filterFreq, filterRQ);
    verb = FreeVerb.ar(sig, reverbMix, reverbRoom, reverbDamp);

	DetectSilence.ar(verb, doneAction: 2);
    Out.ar(out, PanAz.ar(2, verb, pan, 1, 2, 0.5) * amp);
}).add;
)
// Pbindef for controlling the raindrop pattern
(
Pbindef(\rainPattern,
    \instrument, \raindrop,
    \dur, Pexprand(0.13, 0.05, inf) * 8,  // Time between droplets
    \freq, Pexprand(1400, 700, inf),  // Random frequency for each droplet
    \amp, Pexprand(0.05, 0.15, inf),  // Random amplitude
    \pan, Pwhite(-1.0, 1.0, inf),    // Random panning
    \attackTime, 0.001,
    \decayTime, Pexprand(0.11, 0.04, inf),  // Varied decay time
    \filterFreq, Pexprand(900, 2000, inf),  // Random filter frequency
    \filterRQ, Pwhite(0.5, 1.5, inf),
	\wobble, Pwhite(3,7,inf)
).play;

)
meter