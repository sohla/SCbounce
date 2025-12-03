
var m = ~model;
var lastTime = 0;
var frame = 0;

var synth, bassSynth;
var dur = 0.1;
// var notes = [0,2,5,7,9,11,12,14,12,11] + 0;
var notes = [0,2,5,7,11,12,14,16] + 0;
var bass = [2,9,5,12,5,9,2].stutter(2) +0;
var root = [0];
var offset = 0;
var bassCount = 0;
//------------------------------------------------------------


//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.99;
m.rrateMassFilteredDecay = 0.8;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------



SynthDef(\warmRichSynth, {
    |out=0, freq=440, amp=0.5, gate=1,
        attackTime=0.02, decayTime=0.3, sustainLevel=0.5, releaseTime=1.0,
        cutoff=1000, resonance=0.5,
        detune=0.003, stereoWidth=0.5,
        oscMix=0.5, subOscLevel=0.3,
        filterEnvAmount=0.1, filterAttack=0.03, filterDecay=0.1, filterSustain=0.5, filterRelease=0.5|

    var sig, env, filterEnv, subOsc, stereoSig;
    env = EnvGen.kr(
        Env.adsr(attackTime, decayTime, sustainLevel, releaseTime),
        gate,
        doneAction: 2
    );
    sig = Mix.ar([
        Saw.ar(freq * (1 - detune)),
        Saw.ar(freq),
        Saw.ar(freq * (1 + detune))
    ]) * (1 - oscMix) ;
    sig = sig + (SinOsc.ar(freq) * oscMix);
    subOsc = SinOsc.ar(freq * 0.5) * subOscLevel;
    sig = sig + subOsc;
    stereoSig = [sig, sig];
    stereoSig = stereoSig + LocalIn.ar(2);
    stereoSig = DelayC.ar(stereoSig, 0.01, SinOsc.kr(0.1, [0, pi]).range(0, 0.01) * stereoWidth);
    LocalOut.ar(stereoSig * 0.5);
    filterEnv = EnvGen.kr(
        Env.adsr(filterAttack, filterDecay, filterSustain, filterRelease),
        gate
    );
    sig = RLPF.ar(
        stereoSig,
        cutoff * (1 + (filterEnv * filterEnvAmount)),
        resonance.linexp(0, 1, 1, 0.05)
    );
    sig = sig * env * amp * 0.33;    
	// Out.ar(out, DelayN.ar(sig,0.01,[0.007,0.009]));
  Out.ar(out, ((0)!0 ++ sig ++ ((0)!6) ++ sig));


}).add;

//------------------------------------------------------------
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \warmRichSynth,
			\note, Pslide(notes, inf, Pkey(\range), 0, offset),
			\func, Pfunc({|e| ~onEvent.(e)}),
      \cutoff, Pwhite(100,10000),
      \detune, Pwhite(0.001,0.003),
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
~onEvent = {|e|
	// m.com.root = bass[0];
	frame = frame + 1;
};
//------------------------------------------------------------
~next = {|d|

	var dm = m.rrateMassFiltered.lincurve(0,0.8,2,0.7,-2);
	var oct = m.rrateMassFiltered.lincurve(0,0.8,5,7,-2).asInteger;
	var move = m.rrateMassFiltered.lincurve(0,0.8,1,notes.size,-2);
	var amp = m.accelMassFiltered.lincurve(0,1.4,-50,-18,-1);
  var rel = m.rrateMassFiltered.lincurve(0,0.8,0.7,3.0,-1);
	
	Pdef(m.ptn).set(\dur, dur * dm);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\range, move.floor);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\root, root[0]);
	Pdef(m.ptn).set(\releaseTime, rel);

	
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
	[m.rrateMass/2, m.rrateMassFiltered.linlin(0,2,0,1)];

	// X axis
	// [d.sensors.gyroEvent.x/pi]; // norm

	// Y axis
	// [d.sensors.gyroEvent.y/pi]; // norm

	// Z axis
	// [d.sensors.gyroEvent.z/(pi/2)]; // norm

	// device [I• ]
	// [(d.sensors.gyroEvent.x/pi).linlin(-0.8,0.8,0.9,-0.9)]  //up down
	// [(d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,0.9,-0.9)]  //left right
	// [(d.sensors.gyroEvent.z/(pi/2)).linlin(-0.3,1.0,-0.9,0.9)]  //wrist rotate


};




