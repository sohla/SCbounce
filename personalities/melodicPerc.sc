var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;


SynthDef(\melodicPerc, {
    |out=0, freq=50, tension=0.1, decay=0.5, clickLevel=0.3, amp=0.5, dist = 5, dr = 0.01|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch;

    // Pitch envelope
    pitch_contour = Line.kr(1, 0, 0.002);

    // Drum oscillator

	pch = freq * (1 + (pitch_contour * tension));
	drum_osc = SinOsc.ar([pch,pch*1.004], LFNoise2.ar([4,5],10,-10),0.5);

    // Click oscillator
    click_osc = LPF.ar(WhiteNoise.ar(1), 1500);

    // Drum envelope
    drum_env = EnvGen.ar(
        Env.perc(attackTime: 0.105, releaseTime: decay, curve: -4),
        doneAction: 2
    );

    // Click envelope
    click_env = EnvGen.ar(
        Env.perc(attackTime: 0.001, releaseTime: dr),
        levelScale: clickLevel
    );
	sig = (drum_osc * drum_env) + (click_osc * click_env);
	sig = (sig * dist).tanh.distort;
    // Mix and output
    Out.ar(out, Pan2.ar(sig,0,amp))
}).add;


~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \melodicPerc,
			\scale, Scale.major,
			\octave, Pseq([3,4], inf),
			// \note, Pseq([0,1,5,4,-2,5,7,8,4,-2].stutter(23), inf),
			\note, Pseq([7,4,4,2,2,0,-1,-1,-3,-5,-5].stutter(32), inf),
			\legato, 1,
			\amp, Pwhite(0.1,0.2, inf)*0.22,
			// \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);

	Pdef(m.ptn).play(quant:0.1);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	if(e.root != m.com.root,{
		// "key change".postln;
		Pdef(m.ptn).reset;
	});
	Pdef(m.ptn).set(\root, m.com.root);
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linexp(0,2.5,0.5,0.05);
	var dr = m.accelMassFiltered.lincurve(0,2.5,0.01,0.1,2);
  var decay = d.sensors.gyroEvent.z.abs.linlin(0.2,0.8,0.5,0.001);
	
  Pdef(m.ptn).set(\dur, dur);
	// Pdef(m.ptn).set(\decay, decay);
	Pdef(m.ptn).set(\dr, dr);

	if(m.accelMassFiltered > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.1);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
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
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};




