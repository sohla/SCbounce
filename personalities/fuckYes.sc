var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.4;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.9;


SynthDef(\fuckYes, {
    |out=0, gate=1, freq=200, amp=0.0, atk=1.0, dcy=0.6, sus=0.5, rel=4.0, spd=4, idx=0|
	var env = EnvGen.ar(Env.adsr(atk, dcy, sus, rel), gate, doneAction:2);

	var in = LocalIn.ar(2);
	var a = Dseq([1, 3, 2, 5, 4], inf);
	var b = Dseq([0.89, 3, 2, 5.35, 4], inf);
	var c = Dseq([1.27, 3, 1.27, 6.07, 4.0], inf);
	var d = Dseq([1.34, 2, 1, 6.7, 4.0], inf);
	var e = Dseq([1.27, 2, 1, 6.05, 4.0], inf);
	var f = Dseq([1.27, 3, 1.27, 6.05, 4.0], inf);
	var g = Dseq([0.89, 3, 2, 5.35, 4], inf);

	var trigL = Impulse.kr(spd/30);
	var part = Demand.kr(trigL, 0, Dseq([0,0,1,2,3,3,4,5], inf));
	var seq = Dswitch1([a,b,c,d,f,g], part);
	var trig = Impulse.kr(spd);
	var source = SinOsc.ar(freq * Demand.kr(trig, 0, seq) * 0.5, 0,(0.2+amp).distort);
	var sig = Pluck.ar(source + (in * 0.1), Dust.ar(LFCub.ar(1/60,0,2.5).tanh.lag(0.3).linlin(-1,1,1,900)), freq.reciprocal, freq.reciprocal, 1,
        coef:0.5)!2;
	sig = sig.softclip.distort;
	sig = HPF.ar(sig, 100);
	sig = LPF.ar(sig, 1.9e4);
	sig = DelayC.ar(sig,0.3,0.28);
	// sig = GVerb.ar(sig/3);
	LocalOut.ar(sig);
	sig = Mix.ar([sig, source]);
	 sig = PitchShift.ar(sig,0.2, [0.252,0.25] * Demand.kr(trig, 0, seq)) ;
	sig = BHiShelf.ar(sig, 4000, db:-4) * 0.7;
	sig = FreeVerb.ar(sig,0.4,0.9,0.1);
	sig = BRF.ar(sig, 8000,0.9);
	Out.ar(out, sig * env * amp.lag(1.8));
}).add;


~init = ~init <> {
		synth = Synth(\fuckYes, [\gate, 1]);
};
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
// ~onEvent = {|e|
// 	if(e.root != m.com.root,{
// 		// "key change".postln;
// 		Pdef(m.ptn).reset;
// 	});
// 	Pdef(m.ptn).set(\root, m.com.root);
// };


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var amp = m.accelMassFiltered.lincurve(0,2.5,0.0,1.0,-2);
	var spd = m.accelMassFiltered.lincurve(0,2.5,2,9,-2);
	var idx = (d.sensors.gyroEvent.y.abs / pi) * 3;

	var base = [100,200,400];
	var fdx = (d.sensors.gyroEvent.y.abs / pi) * base.size;

	if(amp < 0.1, {amp = 0});

	synth.set(\freq, base[fdx.asInteger]);
	synth.set(\amp, amp * 0.5);
	synth.set(\spd, spd);
	synth.set(\idx, idx.asInteger);



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




