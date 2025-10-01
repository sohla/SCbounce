var m = ~model;
var synth;
var tp =  (m.ptn++"tick");
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.9;

SynthDef(\tick, {
		|out=0, gate=1, amp=0.3, pan=0, dcy=0.2, curve=40, frq = 1000|

		var sig = PinkNoise.ar(EnvGen.ar(Env.perc(0.002,dcy,1,curve.neg), gate, doneAction:2));
        sig = HPF.ar(sig, frq);
		Out.ar(out, Pan2.ar(sig,pan,amp))
}).add;

SynthDef(\melodicPerc, {
    |out=0, freq=50, tension=0.1, decay=0.5, clickLevel=0.3, amp=0.9, dist = 15, dr = 0.003, gate=1|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch, sub;

    // Pitch envelope
    pitch_contour = Line.kr(10, 0, 0.02);

    // Drum oscillator

	pch = freq * (1 + (pitch_contour * tension));
	drum_osc = SinOsc.ar([pch,pch*1.002], LFNoise2.ar([4,5],7,-7),0.5);
	sub = SinOsc.ar(freq * 0.25,0,3);
    // Click oscillator
    click_osc = LPF.ar(WhiteNoise.ar(1), 1100);

    // Drum envelope
    drum_env = EnvGen.ar(
        Env.perc(attackTime: 0.003, releaseTime: decay, curve: -10),
				gate
				// doneAction: Done.freeSelf	
    );

    // Click envelope
    click_env = EnvGen.ar(
        Env.perc(attackTime: 0.001, releaseTime: dr),
        levelScale: clickLevel
    );
	sig = (drum_osc * drum_env) + (click_osc * click_env);
	sig = (sig * dist).tanh.distort;
	sig = sig + (sub * drum_env);

	DetectSilence.ar(sig, doneAction:2);
    // Mix and output
    Out.ar(out, Pan2.ar(sig,0,amp))
}).add;


~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\instrument, \melodicPerc,
			\scale, Scale.major,
			\octave, Pseq([8,9,9,8].stutter(1), inf),
            \note, Pseq([0], inf),
			\amp, Pwhite(0.1,0.2, inf)*0.08,
            \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);


	Pdef(tp,
		Pbind(
			\instrument, \tick,
			\octave, Pseq([7,8,8,7].stutter(1), inf),
			\dur, 0.11,
            \decay, 0.3,
			\pan,Pseg( Pseq([-1,1], inf),Pseq([1,1],inf), \sine, inf),
			\args, #[]
		);
	);


	Pdef(m.ptn).play(quant:0.22);
	Pdef(tp).play(quant:0.22);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Pdef(tp).remove;
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	// if(e.root != m.com.root,{
	// 	// "key change".postln;
	// 	Pdef(m.ptn).reset;
	// });
	Pdef(m.ptn).set(\root, m.com.root);
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linlin(0,2.0,0,2).round;
	var dr = m.accelMassFiltered.lincurve(0,2.5,0.001,0.3,5);
	var decay = d.sensors.gyroEvent.z.abs.linlin(0.2,0.8,2.0,1.0);
	var curve = d.sensors.gyroEvent.z.abs.linlin(0.0,1.0,40.0,10.0);
	var amp = (d.sensors.gyroEvent.z/pi).lincurve(-1.0,1.0,0.0,0.4);
	var frq = m.accelMassFiltered.lincurve.lincurve(0.0,2.5,100,14000,-3);

    Pdef(tp).set(\curve, curve);
    Pdef(tp).set(\amp,amp);
    Pdef(tp).set(\frq,frq);
    
    Pdef(m.ptn).set(\dur, 0.44 / 2.pow(dur));
	Pdef(m.ptn).set(\decay, decay);
	Pdef(m.ptn).set(\dr, dr);
	// Pdef(m.ptn).set(\dist, dr*10);

	if(m.accelMassFiltered > 0.2,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.22);
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




