var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.19;


// SynthDef(\scale2, {
// 	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

//     var env, osc, filt, sig;
//     env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
// 	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
//     filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
//     sig = filt * env * amp * 0.5;
//     sig = Pan2.ar(sig, pan);
//     Out.ar(0, sig.tanh);
// }).add;

SynthDef(\scale2, {
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
	Pdef(m.ptn,
		Pbind(
			\instrument, \scale2,
      \root, Pseq([0,4,8].stutter(240), inf),
      \note, Pseq([0,5,2,9,4], inf),
	    \octave, Pseq([2,3].stutter(2) + 4 , inf),
    	\dur, 0.1,
      // \amp, 1,
	    \attack, Pwhite(0.02,0.06),
      \decay, 0.05,
      \sustain, 0.1,
	    \release, Pwhite(1.3,2.4),
	    // \filterFreq, Pwhite(800,1700),
	    \fq, 0.15,//Pwhite(0.8,0.9),
      \pan, Pseq([-0.9, 0.9], inf),

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);

	Pdef(m.ptn).play(quant:0.1);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
  m.com.root = e.root;
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var notes = [0,2,4,5,9];
	var index = (d.sensors.gyroEvent.y / pi).linlin(-1,1,0,notes.size).floor;
	var note = notes[index];
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.0001,0.7,-1);
  var filterFreq = m.accelMassFiltered.lincurve(0,2,100,2000,-1);

	Pdef(m.ptn).set(\note, note);
	Pdef(m.ptn).set(\amp, amp * 0.5);
  Pdef(m.ptn).set(\filterFreq, filterFreq);

	if(amp > 0.03,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.1);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});

};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 64 );
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|

	var dur = m.accelMassFiltered.linlin(0,2.5,1,4).floor.reciprocal;

	[dur/4];

	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	// [m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};



