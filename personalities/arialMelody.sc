var m = ~model;
var synth;
var lastTime=0;
var notes = [4,6,7,6,7,9];
var roots = [0,3,6].dupEach(12);
var currentNote = notes[0];
var currentRoot = roots[0];
m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.9;


SynthDef(\warmRichSynth, {
    |out=0, freq=440, amp=0.5, gate=1,
        attackTime=0.1, decayTime=0.3, sustainLevel=0.5, releaseTime=1.0,
        cutoff=1000, resonance=0.5,
        detune=0.003, stereoWidth=0.5,
        oscMix=0.5, subOscLevel=0.3,
        filterEnvAmount=0.1, filterAttack=0.03, filterDecay=0.1, filterSustain=0.5, filterRelease=0.5|

    var sig, env, filterEnv, subOsc, stereoSig;

    // ADSR envelope
    env = EnvGen.kr(
        Env.adsr(attackTime, decayTime, sustainLevel, releaseTime),
        gate,
        doneAction: 2
    );

    // Main oscillator (slightly detuned saw waves for richness)
    sig = Mix.ar([
        Saw.ar(freq * (1 - detune)),
        Saw.ar(freq),
        Saw.ar(freq * (1 + detune))
    ]) * (1 - oscMix) ;

    // Add a sine wave oscillator for warmth
    sig = sig + (SinOsc.ar(freq) * oscMix);

    // Sub oscillator for extra depth
    subOsc = SinOsc.ar(freq * 0.5) * subOscLevel;
    sig = sig + subOsc;

    // Stereo widening
    stereoSig = [sig, sig];
    stereoSig = stereoSig + LocalIn.ar(2);
    stereoSig = DelayC.ar(stereoSig, 0.01, SinOsc.kr(0.1, [0, pi]).range(0, 0.01) * stereoWidth);
    LocalOut.ar(stereoSig * 0.5);

    // Filter envelope
    filterEnv = EnvGen.kr(
        Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease),
        gate
    );

    // Apply resonant filter
    sig = RLPF.ar(
        stereoSig,
        cutoff * (1 + (filterEnv * filterEnvAmount)),
        resonance.linexp(0, 1, 1, 0.05)
    );

    // Apply main envelope and output
    sig = sig * env * amp * 0.33;
	Out.ar(out, DelayN.ar(sig,0.01,[0.007,0.009]));
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

	if(move > 0.12, {
		if(TempoClock.beats > (lastTime + 0.35),{
			lastTime = TempoClock.beats;
			notes = notes.rotate(-1);
			currentNote = notes[0];
			roots = roots.rotate(-1);
			currentRoot = roots[0];
			m.com.root = currentRoot;
			synth = Synth(\warmRichSynth, [\freq, (58 + currentNote + currentRoot).midicps, \gate, 1]);
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

/*
var m = ~model;
var synth;
var lastTime = 0;
var notes = [0, 2, 4, 5, 7, 9, 11];
var roots = [0, 3, 5, 7].dupEach(8);
var currentNote = notes[0];
var currentRoot = roots[0];
var lfoRate = 0.1;

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.9;

SynthDef(\dynamicRichSynth, {
    |out=0, freq=440, amp=0.5, gate=1,
    attackTime=0.1, decayTime=0.3, sustainLevel=0.5, releaseTime=1.0,
    cutoff=1000, resonance=0.5,
    detune=0.003, stereoWidth=0.5,
    oscMix=0.5, fmIndex=0.1, ringModFreq=10,
    filterEnvAmount=0.3, filterAttack=0.03, filterDecay=0.1, filterSustain=0.5, filterRelease=0.5,
    lfoRate=0.1, lfoAmount=0.1|

    var sig, env, filterEnv, modulator, carrier, ringMod, lfo, stereoSig;

    // LFO for various modulations
    lfo = SinOsc.kr(lfoRate).range(1 - lfoAmount, 1 + lfoAmount);

    // ADSR envelope
    env = EnvGen.kr(
        Env.adsr(attackTime, decayTime, sustainLevel, releaseTime),
        gate,
        doneAction: 2
    );

    // FM synthesis
    modulator = SinOsc.ar(freq * 2) * fmIndex * freq;
    carrier = SinOsc.ar(freq + modulator) * (1 - oscMix);

    // Add saw wave with detune for richness
    sig = Mix.ar([
        Saw.ar(freq * (1 - detune)),
        Saw.ar(freq),
        Saw.ar(freq * (1 + detune))
    ]) * oscMix;

    sig = sig + carrier;

    // Ring modulation
    ringMod = sig * SinOsc.ar(ringModFreq * lfo);
    sig = XFade2.ar(sig, ringMod, oscMix * 2 - 1);

    // Stereo widening
    stereoSig = [sig, sig];
    stereoSig = stereoSig + LocalIn.ar(2);
    stereoSig = DelayC.ar(stereoSig, 0.01, SinOsc.kr(lfoRate * 0.5, [0, pi]).range(0, 0.01) * stereoWidth);
    LocalOut.ar(stereoSig * 0.4);

    // Filter envelope
    filterEnv = EnvGen.kr(
        Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease),
        gate
    );

    // Apply resonant filter with LFO modulation
    sig = RLPF.ar(
        stereoSig,
        (cutoff * (1 + (filterEnv * filterEnvAmount))) * lfo,
        resonance.linexp(0, 1, 1, 0.05)
    );

    // Apply main envelope and output
    sig = sig * env * amp * 0.33;
    Out.ar(out, sig);
}).add;

~init = ~init <> {
    // Initialize any global variables or settings here
};

~deinit = ~deinit <> {
    // Clean up any resources if needed
};

~onEvent = {|e|
    m.com.root = e.root;
    m.com.dur = e.dur;
};

~next = {|d|
    var move = m.accelMassFiltered.linlin(0, 3, 0, 1);
    var gyroX = d.sensors.gyroEvent.x.abs.linlin(0, 10, 0, 1);
    var gyroY = d.sensors.gyroEvent.y.abs.linlin(0, 10, 0, 1);
    var gyroZ = d.sensors.gyroEvent.z.abs.linlin(0, 10, 0, 1);

    if(move > 0.1, {
        if(TempoClock.beats > (lastTime + 0.25), {
            lastTime = TempoClock.beats;
            notes = notes.scramble;
            currentNote = notes[0];
            roots = roots.rotate(-1);
            currentRoot = roots[0];
            m.com.root = currentRoot;

            synth = Synth(\dynamicRichSynth, [
                \freq, (60 + currentNote + currentRoot).midicps,
                \attackTime, gyroX * 0.5,
                \releaseTime, gyroY * 2,
                \cutoff, gyroZ.linexp(0, 1, 500, 5000),
                \resonance, move,
                \detune, gyroX * 0.01,
                \oscMix, gyroY,
                \fmIndex, move * 5,
                \ringModFreq, gyroZ.linexp(0, 1, 5, 50),
                \lfoRate, gyroX.linexp(0, 1, 0.1, 10),
                \lfoAmount, gyroY * 0.5
            ]);

            synth.server.sendBundle(0.3 + (gyroZ * 0.7), [\n_set, synth.nodeID, \gate, 0]);
        });
    });
};

~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
    [
        m.accelMass * 0.1,
        m.accelMassFiltered.linlin(0, 3, 0, 1),
        d.sensors.gyroEvent.x.abs.linlin(0, 10, 0, 1),
        d.sensors.gyroEvent.y.abs.linlin(0, 10, 0, 1),
        d.sensors.gyroEvent.z.abs.linlin(0, 10, 0, 1)
    ];
};

*/


/*

var m = ~model;
var synth;
var lastTime = 0;
var notes = [0, 2, 4, 5, 7, 9, 11];
var roots = [0, 3, 5, 7].dupEach(8);
var currentNote = notes[0];
var currentRoot = roots[0];

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.9;

SynthDef(\experimentalSynth, {
    |out=0, freq=440, amp=0.5, gate=1,
    grainDur=0.1, grainDensity=20, grainSize=0.1,
    stringDecay=0.9, stringDamp=0.6,
    formantFreq=600, formantBw=100,
    modIndex=1, modFreq=100,
    noiseAmount=0.2, verbMix=0.3, verbRoom=0.6, verbDamp=0.5|

    var exciter, string, formant, gendy, dust, env, sig;

    // Envelope
    env = EnvGen.kr(Env.adsr(0.01, 0.3, 0.5, 1), gate, doneAction: 2);

    // Granular synthesis
    exciter = GrainFM.ar(
        numChannels: 2,
        trigger: Dust.kr(grainDensity),
        dur: grainDur,
        carfreq: freq,
        modfreq: freq * LFNoise1.kr(0.5).range(0.5, 2),
        index: modIndex,
        pan: WhiteNoise.kr(1)
    );

    // Physical modeling (simple string)
    string = Pluck.ar(
        in: exciter,
        trig: Impulse.kr(10),
        maxdelaytime: 1/freq,
        delaytime: 1/freq,
        decaytime: stringDecay,
        coef: stringDamp
    );

    // Formant filter
    formant = Formlet.ar(string, formantFreq, 0.005, formantBw.reciprocal);

    // Gendy oscillator for additional texture
    gendy = Gendy3.ar(
        ampdist: 5,
        durdist: 4,
        adparam: LFNoise1.kr(0.2).range(0.1, 0.9),
        ddparam: LFNoise1.kr(0.2).range(0.1, 0.9),
        freq: freq * 0.5,
        ampscale: 0.3,
        durscale: 0.5
    );

    // Combine signals
    sig = Mix([formant, gendy * 0.3]);

    // Add some noise
    dust = Dust2.ar(100) * noiseAmount;
    sig = sig + dust;

    // Apply envelope
    sig = sig * env * amp;

    // Reverb
    sig = FreeVerb2.ar(sig[0], sig[1], verbMix, verbRoom, verbDamp);

    Out.ar(out, sig);
}).add;

~init = ~init <> {
    // Initialize any global variables or settings here
};

~deinit = ~deinit <> {
    // Clean up any resources if needed
};

~onEvent = {|e|
    m.com.root = e.root;
    m.com.dur = e.dur;
};

~next = {|d|
    var move = m.accelMassFiltered.linlin(0, 3, 0, 1);
    var gyroX = d.sensors.gyroEvent.x.abs.linlin(0, 10, 0, 1);
    var gyroY = d.sensors.gyroEvent.y.abs.linlin(0, 10, 0, 1);
    var gyroZ = d.sensors.gyroEvent.z.abs.linlin(0, 10, 0, 1);

    if(move > 0.08, {
        if(TempoClock.beats > (lastTime + 0.2), {
            lastTime = TempoClock.beats;
            notes = notes.scramble;
            currentNote = notes[0];
            roots = roots.rotate(-1);
            currentRoot = roots[0];
            m.com.root = currentRoot;

            synth = Synth(\experimentalSynth, [
                \freq, (60 + currentNote + currentRoot).midicps,
                \grainDur, gyroX.linexp(0, 1, 0.05, 0.2),
                \grainDensity, gyroY.linexp(0, 1, 10, 50),
                \grainSize, gyroZ.linlin(0, 1, 0.05, 0.2),
                \stringDecay, move.linlin(0, 1, 0.7, 0.99),
                \stringDamp, 1 - move,
                \formantFreq, gyroX.linexp(0, 1, 200, 2000),
                \formantBw, gyroY.linexp(0, 1, 50, 500),
                \modIndex, gyroZ.linexp(0, 1, 0.5, 10),
                \modFreq, move.linexp(0, 1, 50, 500),
                \noiseAmount, gyroX * 0.5,
                \verbMix, gyroY * 0.5,
                \verbRoom, gyroZ * 0.8 + 0.1
            ]);

            synth.server.sendBundle(0.3 + (move * 0.7), [\n_set, synth.nodeID, \gate, 0]);
        });
    });
};

~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
    [
        m.accelMass * 0.1,
        m.accelMassFiltered.linlin(0, 3, 0, 1),
        d.sensors.gyroEvent.x.abs.linlin(0, 10, 0, 1),
        d.sensors.gyroEvent.y.abs.linlin(0, 10, 0, 1),
        d.sensors.gyroEvent.z.abs.linlin(0, 10, 0, 1)
    ];
};

*/