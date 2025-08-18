
var m = ~model;
var frame = 0;
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;

//------------------------------------------------------------
SynthDef(\template, {
    |out=0, gate=1, freq=111, amp=0.3, atk=0.001, rel=2.4|
	var env = EnvGen.ar(Env.perc(atk, rel), gate, doneAction:2);
	var sig = SinOsc.ar(freq);
	Out.ar(out, sig!2 * env * amp * 0.1);
}).add;


//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \template,
			\scale, Scale.major,
			\note, Pseq([0,2,5,9,11]+10, inf),
			\legato, 1,


			\type, \customVisualEvent,
			\shape, Pseq([\circle, \square, \line, \triangle, \star, \hexagon, \cross, \wave, \leaf, \spiral, \blobby], inf),
			//\shape, \line,
			\sx, Pwhite(0,600),
			\sy, 200,
			\ex, Pkey(\sx),
			\ey, 500,
			\startSize, 80,
			\endSize, 10,
			\rotation, pi / Pwhite(1.7,2.3),
			// \startColor, Color.hsv((frame/10.0).mod(1.0),0.5,0.5),//Color.new255(255, 255, 0),
			// \endColor, Color.hsv((frame/10.0).mod(1.0),0.5,0.5),
			\fill, true,

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);
	Pdef(m.ptn).play(quant:0.1);
	// Pdef(m.ptn).set(\sx,0);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
//------------------------------------------------------------
~onEvent = {|e|
	// m.com.root = e.root;
	Pdef(m.ptn).set(\root, m.com.root);
	Pdef(m.ptn).set(\sx, ((e.octave * 12) + e.note).linlin(40,100,0,600));
	// ((e.octave * 12) + e.note).linlin(40,100,0,600).postln;
	frame = frame + 1;
};


//------------------------------------------------------------
~next = {|d|

	var dur = m.rrateMassFiltered.linexp(0,0.3,0.35,0.09);

	// var oct = (d.sensors.gyroEvent.x/pi).linlin(-0.5,0.2,7.0,4.0); //up down
	var oct = (d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,5.0,7.0); //left right

	var amp = m.accelMassFiltered.lincurve(0,2.5,-18,-6,-8);
	var atk = m.accelMassFiltered.lincurve(0,2.5,0.03,0.0001,-3);
	var rel = m.accelMassFiltered.lincurve(0,4.5,0.000001,5.0,-3);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\duration, rel*2.1);
	Pdef(m.ptn).set(\endColor, d.color);
	Pdef(m.ptn).set(\startColor, Color.hsv((frame/10.0).mod(1.0),1.0,1.0));
	Pdef(m.ptn).set(\startWidth, rel*1);
	
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\octave, oct.round -1);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\atk, atk);
	Pdef(m.ptn).set(\rel, rel*0.4);

	if(m.rrateMassFiltered > 0.045,{
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
