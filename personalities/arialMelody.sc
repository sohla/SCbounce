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

	if(move > 0.05, {
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




