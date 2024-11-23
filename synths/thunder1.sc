(
SynthDef(\distantThunder, {
    arg
    // Basic parameters
    out=0, gate=1, amp=0.5,
    // Thunder character
    crackAmount=0.7, rumbleTone=0.5,
    duration=4.0, spread=1.0,
    // Spatial and delay parameters
    delayTime=0.3, decayTime=3.0,
    pan=0.0, distance=0.7;

    var sig, noise, crackle, rumble, env, delays;
    var numDelays = 5;

    // Main envelope for overall thunder shape
    env = EnvGen.kr(
        Env.new(
            [0, 1, 0.7, 0],
            [0.1, duration * 0.3, duration * 0.7],
            [-2, -4, -4]
        ),
        gate,
        doneAction: 2
    );

    // Crack component - filtered noise bursts
    crackle = {
        var crack = BrownNoise.ar;
        var crackEnv = EnvGen.kr(
            Env.perc(0.001, 0.1), gate
        );
        var crackFilter = BPF.ar(
            crack,
            LFNoise1.kr(0.5).range(2800, 8000),
            0.9
        );
        crackFilter * crackEnv * crackAmount
    }.dup;

	crackle = FreeVerb.ar(crackle, 0.7, 0.99, 0.01).distort.tanh + crackle;
    // Rumble component - filtered noise with resonance
    rumble = {
        var baseFreq = LFNoise1.kr(0.5).range(40, 90);
        var filt = BPF.ar(
            BrownNoise.ar,
            baseFreq * LFNoise1.kr(0.2).range(0.8, 1.2),
            0.2
        );
        var res = Resonz.ar(
            filt,
            baseFreq * LFNoise1.kr(0.3).range(1, 2),
            0.1
        );
        filt + (res * rumbleTone)
    }.dup;

    // Combine components
    sig = (crackle * 0.3) + (rumble * 0.7);

    // Distance simulation
    sig = LPF.ar(sig, LinLin.kr(distance, 0, 1, 6000, 2000));
    sig = sig * LinLin.kr(distance, 0, 1, 1, 0.6);

    // Multi-tap delay for large space simulation
    delays = Array.fill(numDelays, { |i|
        var dt = delayTime * (i + 1) * rrand(0.5, 1.5);
        var decay = decayTime * (1 - (i / numDelays));
        var ampScale = (numDelays - i) / numDelays;  // Linear decay for delays
        DelayL.ar(
            sig,
            0.5,
            dt
        ) * ampScale * 0.4
    });

    // Mix original and delays
    sig = Mix([sig] ++ delays);

    // Final processing
    sig = sig * env * amp;
    sig = LeakDC.ar(sig);  // Remove DC offset

    // Output with spread
    sig = Splay.ar(sig, spread, center: pan);

    Out.ar(out, sig);
}).add;
);

(
// Pattern for random thunder claps
Pbindef(\thunderStorm,
    \instrument, \distantThunder,
    \dur, Pwhite(4.0, 15.0, inf),  // Random timing between claps
    \amp, Pwhite(0.3, 0.6, inf),
    \crackAmount, Pwhite(0.4, 0.8, inf),
    \rumbleTone, Pwhite(0.3, 0.7, inf),
    \duration, Pwhite(9.0, 6.0, inf),
    \distance, Pwhite(0.5, 0.9, inf),
    \pan, Pwhite(-0.7, 0.7, inf),
    \delayTime, Pwhite(0.2, 0.4, inf),
    \decayTime, Pkey(\duration) * 0.7,
    \spread, 1
).play;
);

(
Synth(\distantThunder, [
    \amp, 0.6,
    \crackAmount, 10.5,
    \rumbleTone, 0.9,
    \duration, 1.0,
    \spread, 1.0,
    \delayTime, 0.1,
    \decayTime, 4.0,
    \pan, 0.2,
    \distance, 0.3,
    \gate, 1
]);
)

