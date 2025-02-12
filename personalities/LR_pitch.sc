
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
			\scale, Scale.major,
            \octave, 4,
			// \note, Pseq([0,4,7,4,2], inf),
			\legato, 1,
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

	var dur = 0.2;
    var cs = [0,3,7,8];
    // var cs = [0,2,4,5,7,9,11,12];
    // var cs = [0,4,7,11];
    var notes = cs ++ (cs + 12);
    var index = (d.sensors.gyroEvent.y/pi).linlin(-0.5,0.5,0,notes.size); //left right

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\note, notes[index.floor]);

	if(m.accelMassFiltered > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:dur);
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
	// [m.rrateMass, m.rrateMassFiltered.linlin(0,1,0,1)];

	// X axis
	// [d.sensors.gyroEvent.x/pi]; // norm
	
	// Y axis
	// [d.sensors.gyroEvent.y/pi]; // norm

	// Z axis
	// [d.sensors.gyroEvent.z/(pi/2)]; // norm

	// device [I• ]
	[(d.sensors.gyroEvent.x/pi).linlin(-0.8,0.8,0.9,-0.9)]  //up down
	// [(d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,0.9,-0.9)]  //left right
	// [(d.sensors.gyroEvent.z/(pi/2)).linlin(-0.3,1.0,-0.9,0.9)]  //wrist rotate


};




