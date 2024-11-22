var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.99;


SynthDef(\scale1, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

    var env, osc, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
		osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;


~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \scale1,
			\scale, Scale.major,
			\octave, Pseq([4,5].stutter(2), inf),
			\dur, 0.2,
			// \root, 0,//Pseq([0,4,8].stutter(48), inf),
			\attack, Pwhite(0.002,0.03),
    	\decay, 0.2,
    	\sustain, 0.1,
			\release, Pwhite(0.1,2.4),
			\filterFreq, Pwhite(100,10000),
			\fq, Pwhite(0.1,0.9),
    	\pan, Pseq([-0.3, 0.3], inf),

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
	Pdef(m.ptn).set(\root, m.com.root);
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var notes = [0,2,4,5,9];
	var index = (d.sensors.gyroEvent.y / pi).linlin(-1,1,0,notes.size).floor;
	var note = notes[index];
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.0001,1,-1);

	Pdef(m.ptn).set(\note, note);
	Pdef(m.ptn).set(\amp, amp);

	if(amp > 0.07,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.2);
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




