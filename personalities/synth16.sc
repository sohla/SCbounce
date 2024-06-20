var m = ~model;
m.midiChannel = 1;


SynthDef(\synth16, { |out=0, freq=100, gate=1, att=0.1, dec=0.1, sus=0.3, rel=0.3, amp=1.0, dt=0.1|
	var env = EnvGen.ar(Env.adsr(att, dec,sus, rel), gate, doneAction:2);
	var in = LocalIn.ar(2);
	var my = MouseY.kr(1,40);
	var sig = SinOsc.ar([freq, freq + (freq * 0.02)], SinOsc.ar(freq * MouseX.kr(1,10), 0, my, my.neg) * in, 0.1);
	var sub = SinOsc.ar([freq, freq + (freq * 0.02)] * 0.5, 0, 0.5);
	LocalOut.ar(sig * sub);
	sig = (sig + sub) * env * amp;
	// sig = DelayC.ar(sig,2,dt/2, 1, sig);

	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
// Synth(\glockenspiel, [\freq, 880, \amp, 0.2, \decay, 3.5, \pan, -0.5, \hardness, 3.2]);
~init = ~init <> {

	var pat1 = Pbind(
			\instrument, \synth16,
			\octave, Prand([3,4,5], inf),
			\degree, Pxrand([0, 1, 2, 4, 5], inf),
			\dur, Pxrand([0.4, 0.2, 0.1, 0.8], inf),
			\dt, Pkey(\dur),
			\att, Pwhite(0.004, 0.01),
			\dec,  Pwhite(0.1,0.6),
			\sus, 0,
			\amp, 0.5,
			\args, #[],
		);

	var pat2 = Pbind(
			\instrument, \synth16,
			\octave, Prand([6,7,8], inf),
			\degree, Pxrand([0, 1, 2, 4, 5], inf),
			\dur, Pxrand([0.1,0.2,0.1,0.1], inf),
			\dt, Pkey(\dur),
			\amp, 0.3,
			\att, Pwhite(0.004, 0.001),
			\dec, Pwhite(0.04,0.2),
			\rel, 0.07,//Pwhite(0.1, 3) * 0.03,
			\sus, 0.0,
			\args, #[],
		);

	Pdef(m.ptn,Ppar([pat1,pat2], inf));

	Pdef(m.ptn).play(quant:[0.1]);
};
~stop = {
	"stop".postln;
	Pdef(m.ptn).stop();
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;


	e.postln;
};



~onHit = {|state|

	// var vel = 100;
	// var note = 60 + m.com.root - 24	;

	// if(state == true,{
	// 	m.midiOut.noteOn(m.midiChannel, note  , vel);
	// },{
	// 	m.midiOut.noteOff(m.midiChannel, note, 0);
	// });
};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	// var dur = 0.4 * 2.pow(m.accelMassFiltered.linlin(0,4,0,3).floor).reciprocal;
	// Pdef(m.ptn).set(\dur, dur);
	// Pdef(m.ptn).set(\filtFreq, m.accelMassFiltered.linlin(0,4,80,4000));

	if(m.accelMass > 0.2,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).play(quant:[0.1,0,0,0]);
		});
	},{
		if( Pdef(~model.ptn).isPlaying,{
			Pdef(~model.ptn).stop();
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
	// [m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	[m.rrateMass, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};


//
//
//
//
// (
//
// var a = Pbind(
// 	\dur, 0.2,
// 	\note, Pseq([0,2,4,6,8,10], inf)
// );
//
// var b = Pbind(
// 	\dur, 0.3,
// 	\octave, 3,
// 	\note, Pseq([12,8,4,0], inf)
// );
//
// Pdef(\pd, Ppar([a,b], inf));
// Pdef(\pd).play();
// )