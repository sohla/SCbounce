(
SynthDef(\rain, { |out=0, density=10, spatialDepth=0.5, filterFreq=2000, filterRes=0.7, amp=0.3|
    var numDrops, droplets, pan, filtered, verb;

    // Convert density to number of concurrent droplets
    numDrops = (density * 5).clip(1, 50);

    // Create individual water droplets
    droplets = Mix.fill(20, {
        var drop, envelope, time;

        // Random timing for natural variation
        time = Dust.kr(density/numDrops);

        // Basic drop sound using filtered noise
        drop = WhiteNoise.ar *
            EnvGen.ar(
                Env.perc(0.03, Rand(0.01, 0.1)),
                time
            );

        // Spatial positioning based on spatialDepth
        pan = TRand.kr(
            spatialDepth.neg,  // Left boundary
            spatialDepth,      // Right boundary
            time              // Trigger new position for each drop
        );

        // Amplitude adjustment based on pan position to simulate distance
        drop = drop * (1 - (pan.abs * 0.5));  // Quieter when panned further

		verb = FreeVerb.ar(drop,0.4);

        Pan2.ar(verb, pan)
    });

    // Multi-mode filter for timbral control
    filtered = RLPF.ar(
        droplets,
		MouseX.kr(400, 12000, \exponential),
        filterRes
    );

	filtered = HPF.ar(filtered, 3000);
    // Output with master amplitude control
    Out.ar(out, filtered * amp);
}).add;

)

// Basic rain
x = Synth(\rain, [\density, 300, \spatialDepth, 0.7, \filterFreq, 12000]);

// Light drizzle
x.set(\density, 3, \filterFreq, 3000, \spatialDepth, 0.3);

// Heavy downpour
x.set(\density, 200, \filterFreq, 1500, \spatialDepth, 0.8);

// Stop the rain
x.free;