var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;



SynthDef(\fuckYes, {
    |out=0, gate=1, freq=111, amp=0.3, dcy=0.6|
	var env = EnvGen.ar(Env.perc(0.001,dcy), gate, doneAction:2);

	var in = LocalIn.ar(2);
	var source = SinOsc.ar(freq,0,0.2);
	var sig = Pluck.ar(source + (in * 0.2), Dust.ar(900), freq.reciprocal, freq.reciprocal, 1,
        coef:0.5)!2;
	sig = sig.softclip.distort;
	sig = HPF.ar(sig, 200);
	sig = LPF.ar(sig, 1.9e4) * 0.2;
	// sig = DelayC.ar(sig,0.2,0.2);
	// sig = GVerb.ar(sig);
	LocalOut.ar(sig);
	sig = Mix.ar([sig, source]);
	// sig = PitchShift.ar(sig,0.2, 0.5);
	sig = BLowShelf.ar(sig, db:-8);
	Out.ar(out, sig * env * amp);
}).add;


~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \fuckYes,
			// \octave, Pseq([5], inf),
			\root, Pseq([0,-3,2,-1].stutter(60), inf),
			\note, Pseq([0,-2,-5,-7], inf),
			\legato, 1,
			\amp, 1,
			// \func, Pfunc({|e| ~onEvent.(e)}),
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
	if(e.root != m.com.root,{
		// "key change".postln;
		Pdef(m.ptn).reset;
	});
	Pdef(m.ptn).set(\root, m.com.root);
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linexp(0,2.5,0.5,0.09);
	var dr = m.accelMassFiltered.lincurve(0,2.5,0.01,0.1,2);
  var oct = m.accelMassFiltered.lincurve(0,2.5,5,7,2).floor;
  var dcy = m.accelMassFiltered.lincurve(0,2.5,1.2,0.03,-3);
	
  Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\dcy, dcy);
	Pdef(m.ptn).set(\dr, dr);

	if(m.accelMassFiltered > 0.1,{
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
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};



