var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.4;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.9;


SynthDef(\fuckYes, {
    |out=0, gate=1, freq=390, amp=1.0, atk=1.0, dcy=0.6, sus=0.5, rel=4.0|
	var env = EnvGen.ar(Env.adsr(atk, dcy, sus, rel), gate, doneAction:2);

	var in = LocalIn.ar(2);
	var seq = Dseq([1, 3, 2, 5, 4], inf);
	var trig = Impulse.kr(4);
	var f = Demand.kr(trig, 0, seq) * 140 + 240;
	// var source = Saw.ar(freq.lag(0.1),(0.6+amp).distort);
	var source = SinOsc.ar(f,0,(0.6+amp).distort);
	var sig = Pluck.ar(source + (in * 0.1), Dust.ar(900), freq.reciprocal, freq.reciprocal, 1,
        coef:0.5)!2;
	sig = sig.softclip.distort;
	sig = HPF.ar(sig, 200);
	sig = LPF.ar(sig, 1.9e4);
	sig = DelayC.ar(sig,0.3,0.28);
	// sig = GVerb.ar(sig);
	LocalOut.ar(sig);
	sig = Mix.ar([sig, source]);
	 sig = PitchShift.ar(sig,0.2, [0.126,0.125] * Demand.kr(trig, 0, seq));
	sig = BLowShelf.ar(sig, db:-6) * 3;
	Out.ar(out, sig * env * amp.lag(0.1));
}).add;


~init = ~init <> {
	// Pdef(m.ptn,
	// 	Pbind(
	// 		\instrument, \fuckYes,
	// 		// \octave, Pseq([5], inf),
	// 		\root, Pseq([0,-3,2,-1].stutter(60), inf),
	// 		\note, Pseq([0,-2,-5,-7], inf),
	// 		\legato, 1,
	// 		\amp, 1,
	// 		// \func, Pfunc({|e| ~onEvent.(e)}),
	// 		\args, #[]
	// 	);
	// );

	// Pdef(m.ptn).play(quant:0.1);

		synth = Synth(\fuckYes, [\gate, 1]);

};
~deinit = ~deinit <> {
	// Pdef(m.ptn).remove;
	synth.set(\gate, 0);

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

	var amp = m.accelMassFiltered.lincurve(0,2.5,0.0,1.0,-2);
	var trans = 1;//(d.sensors.gyroEvent.y.abs / pi).lincurve(0,1,0,2,2);
	var pchs = [67,75,89,75,61] + (12*trans);
	var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);
	synth.set(\amp, amp);
	// synth.set(\freq, pchs[i.floor].midicps);

  // Pdef(m.ptn).set(\dur, dur);
	// Pdef(m.ptn).set(\octave, oct);
	// Pdef(m.ptn).set(\dcy, dcy);
	// Pdef(m.ptn).set(\dr, dr);

	// if(m.accelMassFiltered > 0.1,{
	// 	if( Pdef(m.ptn).isPlaying.not,{
	// 		Pdef(m.ptn).resume(quant:0.1);
	// 	});
	// },{
	// 	if( Pdef(m.ptn).isPlaying,{
	// 		Pdef(m.ptn).pause();
	// 	});
	// });
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




