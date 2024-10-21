(// Wind and Leaves Synth Definition
SynthDef(\windAndLeaves, {
    |out=0, windIntensity=0.5, leafIntensity=0.5,
     windFilterFreq=1000, leafFilterFreq=3000, filterQ=0.5,
     pan=0, amp=1|

    var wind, leaves, sig, windEnv, leafEnv;

    // Wind sound
    windEnv = LFNoise2.kr(0.1).range(0.8, 1) * windIntensity;
    wind = RLPF.ar(
        PinkNoise.ar(windEnv),
        windFilterFreq * LFNoise2.kr(0.2).exprange(0.8, 1.2),
        filterQ
    );

    // Leaves sound (granular synthesis)
    leafEnv = LFNoise2.kr(0.2).range(0.8, 1) * leafIntensity;
    leaves = GrainIn.ar(
        2,
		trigger: Impulse.kr([LFNoise2.kr(0.5).exprange(50, 150),LFNoise2.kr(0.5).exprange(60, 120)]),
        dur: LFNoise2.kr(0.5).exprange(0.01, 0.05),
        in: BrownNoise.ar(leafEnv),
        pan: LFNoise2.kr(0.5)
    );
    leaves = RHPF.ar(leaves, leafFilterFreq * LFNoise2.kr(0.3).exprange(0.8, 1.2), filterQ);

    // Combine wind and leaves
    sig = (wind * 0.7) + (leaves * 0.3);

    // Apply overall envelope and panning
    sig = sig * EnvGen.kr(Env.asr(2, 1, 2), 1, doneAction: 2);
    sig = Pan2.ar(sig, pan);

    Out.ar(out, sig * amp);
}).add;
)
// Pattern to control the wind and leaves sound
(Pdef(\windAndLeavesPattern,
    Pmono(\windAndLeaves,
        \dur, 0.1,  // Update parameters frequently
        \windIntensity, Pwhite(0.3, 0.7),  // Varying wind intensity
        \leafIntensity, Pwhite(0.2, 0.6),  // Varying leaf intensity
        \windFilterFreq, Pseg(
            Pseq([500, 2000, 500], inf),
            Pwhite(10, 30),
            \sine
        ),  // Slowly changing wind filter frequency
        \leafFilterFreq, Pseg(
            Pseq([7000, 11000, 9000], inf),
            Pwhite(5, 15),
            \sine
        ),  // Slowly changing leaf filter frequency
        \filterQ, Pwhite(0.3, 0.7),  // Varying filter resonance
        \pan, Pseg(
            Pseq([-0.7, 0.7, -0.7], inf),
            Pwhite(20, 40),
            \sine
        ),  // Slow panning
        \amp, 0.5
    )
).play;
)

// Function to start the wind and leaves sound
~startWindAndLeaves = {
    ~windAndLeavesPattern.play;
};

// Function to stop the wind and leaves sound
~stopWindAndLeaves = {
    ~windAndLeavesPattern.stop;
};

// Example usage:
// ~startWindAndLeaves.();  // Start the wind and leaves sound
// ~stopWindAndLeaves.();   // Stop the wind and leaves sound