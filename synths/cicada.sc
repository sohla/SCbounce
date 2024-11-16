(
Ndef(\cicada, {
    |out=0, freq=6000, amp=0.1, pan=0, gate=1,
    filterFreq=3000, filterQ=0.6,
    reverbMix=0.6, reverbRoom=0.5, reverbDamp=0.5|

    var sig, env, filtered, reverbed;

    // Basic cicada sound: a combination of sine waves
    sig = SinOsc.ar(freq) * SinOsc.ar(freq * 0.5) * SinOsc.ar(freq * 0.5) ;

    // Amplitude modulation for the characteristic pulsing
	sig = sig * LFTri.ar(55+ LFNoise2.kr([1,2], 10), 0, MouseX.kr(1,10), 1);

    // Envelope
    env = EnvGen.kr(Env.asr(0.02, 1, 0.1), gate, doneAction: 2);

    // Apply bandpass filter
	filtered = BPF.ar(sig, filterFreq, [0.5,0.4]);
	filtered = HPF.ar(filtered, 6000);


    // Apply reverb
    reverbed = FreeVerb.ar(filtered, reverbMix, reverbRoom, reverbDamp);

    // Final output with panning and envelope
    Out.ar(out, Pan2.ar(reverbed * env * amp, pan));
}).play;
)
