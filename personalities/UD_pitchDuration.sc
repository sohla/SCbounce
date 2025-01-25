

var m = ~model;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, rel=0.5, clickLevel=0.5, amp=0.5, dist = 5, atk = 0.005, gate=1|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch;

    // Pitch envelope
    pitch_contour = Line.kr(1, 0, 0.05 * rel.reciprocal);

    // Drum oscillator

	pch = freq * (1 + (pitch_contour * tension));
	drum_osc = SinOsc.ar([pch,pch*1.004], LFNoise2.ar([4,5],3,-3),0.5);

    // Click oscillator
    click_osc = LPF.ar(WhiteNoise.ar(1), 340);

    // Drum envelope
    drum_env = EnvGen.ar(
        Env.adsr(atk, atk, 0.1, rel), gate,
        doneAction: 2
    );

    // Click envelope
    click_env = EnvGen.ar(
        Env.perc(attackTime: 0.01, releaseTime: 0.01),
        levelScale: clickLevel
    );
	sig = (drum_osc * drum_env) + (click_osc * click_env);
	sig = (sig * dist).tanh.distort;
    // Mix and output
    Out.ar(out, Pan2.ar(sig,0,amp))
}).add;


//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \versatilePerc,
			\scale, Scale.major,
			\note, Pseq([0], inf),
			\legato, 1,
			\octave, Pseq([3].stutter(2), inf),
			\tension, Pwhite(0,0.1,inf),
			\clickLevel, Pwhite(0.07,0.12,inf),
			\dist, Pwhite(1,15,inf),
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

	var dur = (d.sensors.gyroEvent.x/pi).linlin(-0.5,0.5,0.08,0.4);
	var amp = m.accelMassFiltered.lincurve(0,2.5,-28,-8,-1);
	var atk = (d.sensors.gyroEvent.x/pi).lincurve(-0.4,0.5,0.005,0.5,1).lag(1);
	var clickLevel = (d.sensors.gyroEvent.x/pi).linlin(-0.5,0.5,0.5,0,-2);
    var cs = [0,5,10,15,24];
    var notes = cs ++ (cs + 24);
	var index = (d.sensors.gyroEvent.x/pi).linlin(-0.5,0.5,notes.size,0.0); //up down

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\clickLevel, clickLevel);
	Pdef(m.ptn).set(\root, notes[index.floor]);
	Pdef(m.ptn).set(\atk, atk);

	if(m.accelMassFiltered > 0.07,{
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
	[(d.sensors.gyroEvent.x/pi).linlin(-1.0,1.0,0.9,-0.9)]  //up down
	// [(d.sensors.gyroEvent.y/pi).linlin(-1.0,1.0,0.9,-0.9)]  //left right
	// [(d.sensors.gyroEvent.z/(pi/2)).linlin(-1.0,1.0,-0.9,0.9)]  //wrist rotate

	// example of wrapping around mapping -90 to 90 to 0.9 to -0.9
	// var val = (d.sensors.gyroEvent.x/pi) * 2.0;
	// if(val > 1.0, {val = 2.0 - val});
	// if(val < -1.0, {val = -2.0 - val});
	// [val.linlin(-1.0,1.0,-0.9,0.9)]  //up down

	
};




// Pitch + Duration 
// Pitch + Timbre 
// •Pitch + Dynamics 
// Duration + Timbre 
// Duration + Dynamics 
// Timbre + Dynamics
