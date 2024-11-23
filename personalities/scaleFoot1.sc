var m = ~model;
var synth;
var lastTime=0;
var notes = [0,2,4,5,9] - 36;
var octave = [0,12].stutter;
var currentNote = notes[0];
var currentRoot = 0;
m.accelMassFilteredAttack = 0.88;
m.accelMassFilteredDecay = 0.9;

// SynthDef(\scaleFoot1, {
// 	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

//     var env, osc, filt, sig;
//     env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
// 	  osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
//     filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
//     sig = filt * env * amp * 0.5;
//     sig = Pan2.ar(sig, pan);
//     Out.ar(0, sig.tanh);
// }).add;

SynthDef(\scaleFoot1, {
    |freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, 
    release = 0.3, gate = 1, filterFreq = 800, fq = 0.5, pan = 0|
    
    var env, osc, filt, sig;
    var freqMod = freq * [1, 1.004];  // Calculate frequency modulation once
    
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
    
    // Combine oscillators into single array operation
    osc = Mix([
        Saw.ar(freqMod),
        SinOsc.ar([freq-1, (freq-1) * 0.995]),  // Simplified multiplication
        LFTri.ar([freq+1, freqMod[1]])
    ]);
    
    // Single tanh operation after mixing
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.25;  // Combined scaling factors
    sig = Pan2.ar(sig, pan);
    
    Out.ar(0, sig.tanh);
}).add;

~init = ~init <> {
};

~deinit = ~deinit <> {
};

//------------------------------------------------------------
// ~onEvent = {|e|
// 	// m.com.root = e.root;
// 	// m.com.dur = e.dur;
// };
//------------------------------------------------------------
~next = {|d|

	var move = m.accelMassFiltered.linlin(0,3,0,1);
  var filterFreq = m.accelMass.lincurve(1,2.5,50,1100,-2);

	if(move > 0.17, {
		if(TempoClock.beats > (lastTime + 0.4),{
			lastTime = TempoClock.beats;
			notes = notes.rotate(-1);
			octave = octave.rotate(-1);
			currentNote = notes[0];
			currentRoot = m.com.root;
			synth = Synth(\scaleFoot1, [
				\freq, (60 + currentNote + currentRoot + octave[0]).midicps,
				\amp, 1,
        \attack, 0.003,
        \decay, 0.2,
        \sustain, 0.7,
	      \release, 1.7,
	      \filterFreq, filterFreq,
	      \fq, 0.5,
        \pan, -1.0.rrand(1.0)
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
