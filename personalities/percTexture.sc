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

SynthDef(\pullstretchMono, {|out, amp = 0.8, buffer = 0, envbuf = -1, pch = 1.0, div=1, speed = 0.01, splay = 0.5|
	var pos;
	// var mx,my;
	var sp;
	var mas;
	var len = BufDur.kr(buffer) / div;
	var lfo = LFSaw.kr( (1.0/len) * speed ,1,0.5,0.5);
	// my = MouseY.kr(0.01,1,1.0);//splay


	sp = Splay.arFill(12,
			{ |i| Warp1.ar(1, buffer, lfo, pch,splay, envbuf, 8, 0.1, 2)  },
			1,
			1,
			0
	) * amp;

	mas = HPF.ar(sp,245);

	Out.ar(out,mas);
}).add;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	~sampleFolder = PathName("/Users/soh_la/Downloads/Voice recordings Music in Motion 25June2024/converted");
	Buffer.read(s, ~sampleFolder.entries[6].fullPath, action:{|buf|
		"loaded".postln;
		sa = Synth(\pullstretchMono,[\buffer,buf,\pch,0.midiratio, \amp,0.4, \div, 4]);
		sb = Synth(\pullstretchMono,[\buffer,buf,\pch,12.midiratio, \amp,0.3, \div, 4]);
		// sc = Synth(\pullstretchMono,[\buffer,buf,\pch,7.midiratio, \amp,0.2, \div, 4]);
	});



	Pdef(m.ptn,
		Pbind(
			\instrument, \synth2211,
			\octave, Pxrand([3,4,5], inf),
			\note, Pseq([0], inf),
			\root, Pseq([0,3,-2,-4].stutter(24)-3, inf),
			// \dur, Pxrand([0.2,0.2,0.2,0.1,0.1,0.2] * 2, inf),
			\rel, Pwhite(0.002, 0.9, inf),
			\amp, Pkey(\octave).linlin(3,6,1,0.2),
			\shp, Pwhite(0.9,0.002, inf),
			\args, #[],

		)
	);

	Pdef(m.ptn).play(quant:0.25);

};


~deinit = {
	sa.free;
	sb.free;
	// sc.free;
	Pdef.removeAll;
	s.freeAllBuffers;

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


	var dur = m.accelMassFiltered.linexp(0,1,0.8,0.2);
	var amp = m.accelMassFiltered.linlin(0,1,0,0.4);


	Pdef(m.ptn).set(\dur, dur);

	// var dur = m.accelMassFiltered.linlin(0,1,0.5,0.02);
	// var start = m.accelMass.linlin(0,0.5,0.5,0.8);
	//
	// Pdef(m.ptn).set(\amp, amp);
	// Pdef(m.ptn).set(\dur, dur);
	// Pdef(m.ptn).set(\start, start);

	// Pdef(m.ptn).set(\filtFreq, m.accelMassFiltered.linexp(0,4,180,14000));
	//
	if(m.accelMass > 0.1,{
		if( Pdef(~model.ptn).isPlaying.not,{
			Pdef(~model.ptn).resume(quant:0.25);
		});
		},{
			if( Pdef(~model.ptn).isPlaying,{
				Pdef(~model.ptn).pause();
			});
	});

	sa.set(\speed, m.rrateMass.linlin(3,10,0.01,2));
	sb.set(\speed, m.rrateMass.linlin(3,10,0.01,2));
	sc.set(\speed, m.rrateMass.linlin(3,10,0.01,2));

	sa.set(\splay, m.rrateMass.linlin(3,10,0.01,1));
	sb.set(\splay, m.rrateMass.linlin(3,10,0.01,1));
	sc.set(\splay, m.rrateMass.linlin(3,10,0.01,1));

	sa.set(\amp, amp);
	sb.set(\amp, amp);
	sc.set(\amp, amp);
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
