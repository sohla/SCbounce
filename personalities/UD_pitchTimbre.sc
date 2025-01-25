

var m = ~model;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\toneNoise, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0, mix = 0.5|

    var env, osc, filt, sig, lfo;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
    lfo  = LFCub.ar(6,0,2);
	  osc = SelectX.ar(mix,
      [ 
        LFTri.ar([freq+1, freq * 1.007]+lfo,0,1),
        RLPF.ar(PinkNoise.ar(0.3),[freq, freq * 1.004] + lfo,0.02),
      ]);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;


//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
      \instrument, \toneNoise,
      \note, Pseq([0,2], inf),
      \dur, 0.3,
      \amp, 0.4,
      \pan, Pseq([-0.3, 0.3], inf),
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

  var dur = 0.3;

  var attack = (d.sensors.gyroEvent.x/pi).lincurve(-0.5,0.5,0.34,0.002,-2);
  var decay = (d.sensors.gyroEvent.x/pi).lincurve(-0.5,0.5,0.2,0.2,-2);
  var sustain = (d.sensors.gyroEvent.x/pi).lincurve(-0.5,0.5,0.8,0.01,-2);
  var release = (d.sensors.gyroEvent.x/pi).lincurve(-0.5,0.5,2.2,0.1,-2);
  var mix = (d.sensors.gyroEvent.x/pi).lincurve(-0.5,0.5,1.0,0.0,-1);
  var cs = [3,4,5,6,7];
  var notes = cs;
	var index = (d.sensors.gyroEvent.x/pi).linlin(-0.2,0.2,notes.size,0.0); //up down

	Pdef(m.ptn).set(\octave, notes[index.floor]);
	Pdef(m.ptn).set(\attack, attack);
	Pdef(m.ptn).set(\decay, decay);
	Pdef(m.ptn).set(\sustain, sustain);
	Pdef(m.ptn).set(\release, release);
	Pdef(m.ptn).set(\mix, mix);

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
	// [(d.sensors.gyroEvent.x/pi).linlin(-1.0,1.0,0.9,-0.9)]  //up down
	[(d.sensors.gyroEvent.y/pi).linlin(-1.0,1.0,0.9,-0.9)]  //left right
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
