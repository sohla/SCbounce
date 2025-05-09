
var m = ~model;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;

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
			\instrument, \template,
			// \type, \customEvent,
			\scale, Scale.major,
			\note, Pseq([0,4,7,11], inf),
			\viewName, "192.168.200.11",
			\legato, 1,
			\root, Pseq([0,-2].stutter(8*4), inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);
	Pdef(m.ptn).play(quant:0.1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
};


//------------------------------------------------------------
~next = {|d|

	var dur = m.rrateMassFiltered.linexp(0,0.3,0.35,0.07);

	var oct = (d.sensors.gyroEvent.x/pi).linlin(-0.5,0.2,5.0,2.0); //up down
	// var oct = (d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,3.0,8.0); //left right

	var amp = m.accelMassFiltered.lincurve(0,2.5,-28,-13,-3);
	var atk = m.accelMassFiltered.lincurve(0,2.5,0.03,0.0001,-3);
	var rel = m.accelMassFiltered.lincurve(0,2.5,0.2,1.0,-1);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\octave, oct.round);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\atk, atk);
	Pdef(m.ptn).set(\rel, rel);

	if(m.rrateMassFiltered > 0.01,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.35);
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
