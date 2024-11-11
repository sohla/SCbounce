var m = ~model;
var synth;
var lastTime=0;
var notes = [0,8,3,9,5,6,14,2,8,4,9,11,3,6,2] * 6;
var roots = [0,9,8].dupEach(12);
// var notes = [0,1,4,5,7,8,11,12,14] + 24;
// var roots = [0].dupEach(18);
var currentNote = notes[0];
var currentRoot = roots[0];
m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.9;

SynthDef(\largeGong, {
    arg
    // Basic parameters
    out=0, freq=70, amp=0.5, pan=0,
    // Gong characteristics
    strikeForce=0.7,    // Impact intensity
    shimmerAmount=0.6,  // Amount of characteristic gong wobble
    metallic=0.7,       // Metallic character
    size=0.8,          // Size affect harmonics and decay
    // Time and space
    attackTime=0.002,
    decayTime=5.0,     // Long decay for large gong
    roomSize=0.9,
    damping=0.3,
    mix=0.5;

    var strike, partials, gongSound, shimmer, env, output;
    var numPartials = 6;
    var baseFreq = freq;

    // Complex frequency ratios for large gong
    var ratios = [
        1,      // Fundamental
        1.36,   // Major third overtone
        1.97,   // Dominant overtone
        2.43,   // Characteristic gong partial
        3.24,   // Upper partial
        4.16,   // High partial
        5.61,   // Shimmer frequency 1
        6.15,   // Shimmer frequency 2
        7.20,   // High resonance 1
        8.33,   // High resonance 2
        10.47,  // Upper shimmer 1
        12.65   // Upper shimmer 2
    ];

    // Initial strike sound
    strike = Mix([
        // Metal impact
        HPF.ar(WhiteNoise.ar, 1000) * Env.perc(0.001, 0.01).ar,

        // Low thump
        SinOsc.ar(baseFreq * [0.5, 1]) * Env.perc(0.001, 0.08).ar,

        // Mid frequencies
        BPF.ar(PinkNoise.ar, baseFreq * 2, 0.2) * Env.perc(0.001, 0.05).ar
    ]) * strikeForce * 0.3;

    // Shimmer effect - slow beating frequencies
    shimmer = SinOsc.kr(
        [0.15, 0.17, 0.23, 0.27] * shimmerAmount,
        mul: shimmerAmount * 0.4,
        add: 1
    );

    // Create partial frequencies with individual envelopes
    partials = Mix.fill(numPartials, { |i|
        var env, vol, freq, beating;

        // Each partial gets longer decay time
        env = Env.perc(
            attackTime: attackTime,
            releaseTime: decayTime * (1 + (i * 0.3)) * size,
            level: 1.0 / (i + 1),
            curve: [-2, -4]
        ).ar;

        // Slight random frequency variations
        freq = baseFreq * ratios[i] * LFNoise2.kr(0.1).range(0.999, 1.001);

        // Volume based on partial number and metallic parameter
        vol = (1 / (i + 1)) * (1 - (i * 0.5 * (1 - metallic)));

        // Beating patterns for each partial
        beating = SinOsc.kr(
            shimmerAmount * 0.2 * (i + 1),
            mul: 0.1,
            add: 1
        );

        // Combine oscillators with slight detuning
        Mix([
            SinOsc.ar(freq),
            SinOsc.ar(freq * 1.001),
            SinOsc.ar(freq * 0.999)
        ]) * env * vol * beating
    });

    // Combine strike and partials
    gongSound = (strike * 0.3) + (partials * 0.7);

    // Apply shimmer modulation
    gongSound = gongSound * shimmer;

    // Overall envelope
    env = Env.perc(
        attackTime: attackTime,
        releaseTime: decayTime,
        level: amp,
        curve: [-2, -4]
    ).ar(doneAction: 2);

    // Basic processing
    gongSound = LeakDC.ar(gongSound);
    gongSound = LPF.ar(gongSound, 6000);
    gongSound = HPF.ar(gongSound, 30);

    // Create stereo field
    output = Splay.ar(gongSound, spread: size.linlin(0, 1, 0.3, 0.8));

    // Add space
	// output = FreeVerb2.ar(
	// 	output[0],
	// 	output[1],
	// 	mix,
	// 	roomSize,
	// 	damping
	// );
	output[0]= CombL.ar(output[0], 0.1, [0.0297, 0.0371, 0.0411, 0.0437], 2, roomSize).sum * 0.2;
	output[1] = CombL.ar(output[1], 0.1, [0.0277, 0.0353, 0.0389, 0.0419], 2, roomSize).sum* 0.2;

    // Final shaping
    output = output * env;
    output = Limiter.ar(output, 0.95);

    Out.ar(out, output);
}).add;

~init = ~init <> {
};

~deinit = ~deinit <> {
};

//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};
//------------------------------------------------------------
~next = {|d|

	var move = m.accelMassFiltered.linlin(0,3,0,1);
	var metal = m.accelMassFiltered.linlin(0,2.5,0.01,2);
	var size = m.accelMassFiltered.linlin(0,2.5,0.1,1);

	if(move > 0.22, {
		if(TempoClock.beats > (lastTime + 0.35),{
			lastTime = TempoClock.beats;
			notes = notes.rotate(-1);
			currentNote = notes[0];
			roots = roots.rotate(-1);
			currentRoot = roots[0];
			m.com.root = currentRoot;
			synth = Synth(\largeGong, [
				\freq, 33.midicps,
				\gate, 1,
				\amp, 0.3,
    			\strikeForce, size,    // Impact intensity
    			\shimmerAmount, 0.1,  // Amount of characteristic gong wobble
				\metallic, metal,       // Metallic character
    			\size, size,          // Size affect harmonics and decay

			]);
			synth.server.sendBundle(0.3,[\n_set, synth.nodeID, \gate, 0]);
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
