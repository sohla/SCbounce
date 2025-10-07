

var m = ~model;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.2;

//------------------------------------------------------------
SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, rel=0.5, clickLevel=0.5, amp=0.5, dist = 5, atk = 0.005, gate=1|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch;
    pitch_contour = Line.kr(1, 0, 0.05 * rel.reciprocal);
		pch = freq * (1 + (pitch_contour * tension));
		drum_osc = SinOsc.ar([pch,pch*1.004], LFNoise2.ar([4,5],3,-3),0.5);
    click_osc = LPF.ar(WhiteNoise.ar(1), 340);
    drum_env = EnvGen.ar(Env.adsr(atk, atk, 0.1, rel), gate, doneAction: 2);
    click_env = EnvGen.ar(Env.perc(attackTime: 0.01, releaseTime: 0.01), levelScale: clickLevel);
		sig = (drum_osc * drum_env) + (click_osc * click_env);
		sig = (sig * dist).tanh.distort;
    Out.ar(out, Pan2.ar(sig,0,amp))
}).add;

//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \versatilePerc,
			\scale, Scale.major,
			\note, Pseq([0,2], inf),
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

	var amp = m.accelMassFiltered.lincurve(0,2.5,-28,-16,-1);
	var rel = m.accelMassFiltered.lincurve(0.0,2.5,1.2,0.5,-2);
	var dur = m.accelMassFiltered.lincurve(0.0,2.5,0.5,0.1,-4);
	var clickLevel = m.accelMassFiltered.lincurve(0.0,2.5,0.0,0.5,3);
	var atk = m.accelMassFiltered.lincurve(0.0,2.5,0.2,0.002,-1);


	var cs = [0,5,10,15,24];
	var notes = cs ++ (cs + 24);
	var index = (d.sensors.gyroEvent.z / pi).linlin(-1.0,1.0,notes.size,0); //left right

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\rel, rel);
	Pdef(m.ptn).set(\atk, atk);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\clickLevel, clickLevel);
	Pdef(m.ptn).set(\root, notes[index.floor]);

	if(m.accelMassFiltered > 0.2,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.1);
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

	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered] * 0.2;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	[m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];


};





// Pitch + Duration 
// Pitch + Timbre 
// •Pitch + Dynamics 
// Duration + Timbre 
// Duration + Dynamics 
// Timbre + Dynamics
