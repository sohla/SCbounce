var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.6;
m.accelMassFilteredDecay = 0.06;

SynthDef(\beeSynth1, { |out=0, rr=0.1, amp = 1.0, gate = 1, release = 2|

		var ampa = Lag.ar(K2A.ar(rr), 0.5) * 2.0 * amp;

		var lrr = Lag.ar(K2A.ar(rr), 0.5);
	var pitch = LinLin.ar(lrr, 0.0, 1.0, 264.rrand(300), 398.rrand(430));
		var ffrq = LinLin.ar(lrr, 0.0, 1.0, 811, 2398);
		var ramp = LinLin.ar(lrr, 0.0, 1.0, 5, 22);

		var sig = HPF.ar(
			LFSaw.ar(
				StandardL.ar(11, ramp) * 14 + pitch,
				1,
				0.9
			),
		ffrq)!2;

		sig = sig * EnvGen.kr(Env.adsr(0.1, 0.1, 1, release), gate: gate, doneAction: Done.freeSelf) * ampa;
		// sig = sig * ampa;

		Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	synth = Synth(\beeSynth1);
};


~deinit = ~deinit <> {
	synth.free;
	s.freeAllBuffers;

};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var amp = m.accelMassFiltered.linlin(0,1,0,0.4);
	var rate = m.accelMassFiltered.lincurve(0.0,2.5,0.0,1.0,5);
	var rr = m.rrateMassFiltered.linlin(0,1,1,1.1);
	synth.set(\amp, amp);
	synth.set(\rr, rate * rr);
};

~nextMidiOut = {|d|
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

// (
// var a = 1.0.linrand;
// var b = Array.linrand(1,0.0,1.0-a);
// var c = 1.0 - b - a;
// [a,b,c].flat
// )
//
