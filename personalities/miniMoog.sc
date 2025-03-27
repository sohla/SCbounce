
var m = ~model;

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;

//------------------------------------------------------------
SynthDef(\miniMoog, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

    var env, osc, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \miniMoog,
            \note, Pseq([0,5,2,9,4], inf),
            \octave, Pseq([1,2,3].stutter(2) + 2, inf),
            \root, Pseq([0,4,8].stutter(60), inf),
            \amp, 0.3,
            \attack, Pwhite(0.02,0.09),
            \decay, 0.1,
            \sustain, 0.1,
            \release, Pwhite(0.1,2.4),
            \fq, 0.3,
            \pan, Pseq([-0.3, 0.3], inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);
	Pdef(m.ptn).play(quant:0.1);
	Pdef(m.ptn).pause;
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

	// var ff = (d.sensors.gyroEvent.x/pi).linexp(-0.5,0.5,300,10000); 
	// var oct = (d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,3.0,8.0); //left right

	var amp = m.accelMassFiltered.lincurve(0,2.5,-28,-13,-3);
	var atk = m.accelMassFiltered.lincurve(0,2.5,0.03,0.0001,-3);
	var rel = m.accelMassFiltered.lincurve(0,2.5,0.01,1.0,-1);
	var ff = m.accelMassFiltered.linexp(0,0.5,60,18000);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\atk, atk);
	Pdef(m.ptn).set(\rel, rel);
    Pdef(m.ptn).set(\filterFreq, ff);

	if(m.rrateMassFiltered > 0.01,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.15);
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
