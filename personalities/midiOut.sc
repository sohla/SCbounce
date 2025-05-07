
var m = ~model;
// filter parameters
m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.9;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.9;
//------------------------------------------------------------
SynthDef(\template, {
    |out=0, gate=1, freq=111, amp=0.3, atk=0.001, rel=0.4|
	var env = EnvGen.ar(Env.perc(atk, rel), gate, doneAction:2);
	var sig = SinOsc.ar(freq * [1.0,1.0027]);
	Out.ar(out, sig * env * amp);
}).add;
//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\type, \midi,
			\midiout, m.midiOut,
			\amp,0.8,
			// \instrument, \template,

			// \dur, Pseq([Pseq([Rest(0.25), 0.25], 5) ,0.125,0.125, 0.25]* 0.8, inf),
			\octave, Pseq([4,5,6], inf),
		    \note, Pseq([0,2,5,9,11,9,7,4], inf),
			\root, Pseq([0].stutter(16), inf),
			// \dur, 0.125 * 0.5,
			// \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);
	// Pdef(m.ptn).set(\dur,0.2);
	Pdef(m.ptn).play(quant:0.125);

};
//------------------------------------------------------------
~deinit = ~deinit <> {
	// free resources here
		Pdef(m.ptn).remove;

};
//------------------------------------------------------------
~onEvent = {|e|
	// update with community model here
	// e.postln;
};
//------------------------------------------------------------
~next = {|d|
	// update with device data here
	// m.midiOut.noteOn(16, 60, 60);

	var dur = m.accelMassFiltered.lincurve(0,2,0.5,0.125,-3);

	Pdef(m.ptn).set(\dur, dur);
	if(m.accelMassFiltered > 0.12,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.125);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// ACCEL
	// [m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];

	// ROTATE
	[m.rrateMass, m.rrateMassFiltered.linlin(0,1,0,1)];

	// X axis
	// [d.sensors.gyroEvent.x/pi]; // norm

	// Y axis
	// [d.sensors.gyroEvent.y/pi]; // norm

	// Z axis
	// [d.sensors.gyroEvent.z/(pi/2)]; // norm

	// device [I• ]
	// [(d.sensors.gyroEvent.x/pi).linlin(-0.5,0.5,0.9,-0.9)]  //up down
	// [(d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,0.9,-0.9)]  //left right
	// [(d.sensors.gyroEvent.z/(pi/2)).linlin(-0.3,1.0,-0.9,0.9)]  //wrist rotate


};




// •Pitch + Duration
// •Pitch + Timbre
// •Pitch + Dynamics
// Duration + Timbre
// Duration + Dynamics
// Timbre + Dynamics
