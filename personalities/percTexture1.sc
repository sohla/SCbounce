var m = ~model;
var sa, sb, sc;
m.midiChannel = 1;

SynthDef(\synth2211, { |out=0, gate=1, freq=100, rel=0.1, amp=0.1, shp= 0.09|
	var env = EnvGen.ar(Env.perc(rel.linlin(0.002,0.4,0.001,0.01), rel), gate, [1, 0.2, 0.04, 0.02], doneAction:0);
	var sig = DynKlang.ar(`[ [1,3,5,7] * freq * LFNoise2.ar(30).linlin(-1,1,0.98,1.02), env, [[0,pi,0],[pi, 0, pi]]], 1, 0) * 0.3;
	var sub = SinOsc.ar(freq * 0.5, [0,pi.half], 0.1 * env);
	var rev = CombL.ar(sig + sub, 0.2, [0.07, 0.075] * SinOsc.ar(freq).linlin(-1,1,1,1+shp), 0.2);
	DetectSilence.ar(rev, doneAction: Done.freeSelf);
	Out.ar(out, (DelayN.ar(sub,0.1,[0.07,0.09], 14) + rev) * amp * 0.7);
}).add;


//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\instrument, \synth2211,
			\octave, Pxrand([3,4,5], inf),
			\note, Pseq([0], inf),
			\root, Pseq([0,3,-2,-4].stutter(24)-3, inf),
			// \dur, Pxrand([0.2,0.2,0.2,0.1,0.1,0.2] * 2, inf),
			\rel, Pwhite(0.002, 0.9, inf),
			\amp, Pkey(\octave).linlin(3,6,1,0.2) * Pkey(\da),
			\shp, Pwhite(0.9,0.002, inf),
			\args, #[],

		)
	);

	Pdef(m.ptn).play(quant:0.25);

};


~deinit = {
	Pdef.removeAll;

};
~stop = {
	"stop".postln;
	Pdef(~model.ptn).stop();

};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	// m.com.root = e.root;
	// m.com.dur = e.dur;

	// m.com.root.postln;
	// Pdef(m.ptn).set(\root, m.com.root);
};

~onHit = {|state|
};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|


	var dur = m.accelMassFiltered.linexp(0.0,1.0,5,0.18);
	var amp = m.accelMassFiltered.linexp(0.0,1.0,0.05,0.6);
	if(dur < 0.1, { dur = 0.18});

	Pdef(m.ptn).set(\da, amp);
	Pdef(m.ptn).set(\dur, 0.25);

	if(m.accelMass > 0.1,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).resume(quant:0.25);
		});
		},{
			if( Pdef(~model.ptn).isPlaying,{
				Pdef(~model.ptn).pause();
			});
	});

};

~nextMidiOut = {|d|
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

// (
// var a = 1.0.linrand;
// var b = Array.linrand(1,0.0,1.0-a);
// var c = 1.0 - b - a;
// [a,b,c].flat
// )
//
