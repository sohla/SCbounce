/*
gestures:    [strike, tilt]
description: Basic 8th-note rock beat (K H S H K H S H), phase-aligned to the downbeat via a clock-derived slot lookup. Pattern runs while the player is moving and pauses when still.
sound:       Compressed drum-kit samples with FreeVerb tail; RLPF applied.
pitch:       None — sample kit. Rate driven by y-tilt.
rhythm:      Uniform 8th grid (dur=2), phase-locked to the bar.
instruments: [Gravitone]
*/

var m = ~model;
var lastTime = 0;
var bi = -1;
var dur = 2;
// bar length in clock beats. the conductor supplied this as
// ~scoreBeatsPerBar * ~scoreEventsPerBeat; standalone it is local.
var barLen = 16;
// music-downbeat alignment in clock beats; tune by ear so drums[0] lands on the downbeat.
var phase = 4;
var group;

// cycle: K H S H K H S H
var drums = [0, 2, 5, 2, 0, 2, 5, 2];

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;

//------------------------------------------------------------
SynthDef(\drumkitt2, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.01, sustain=0.3, release=0.4, gate=1, cutoff=14000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) * env;
	sig = RLPF.ar(sig, cutoff, rq);
	sig = Compander.ar(sig, sig,
		thresh: -15.dbamp,
		slopeBelow: 1,
		slopeAbove: 0.5,
		clampTime:  0.01,
		relaxTime:  0.01
	);
	sig = FreeVerb.ar(sig, 0.1, 1.1, 0.4);
	Out.ar(out, sig * amp);
}).add;

//------------------------------------------------------------
// visual : one mark per hit, laid across the canvas by slot in the bar —
// the 8th grid becomes a horizontal staff, so the beat reads left to
// right and restarts every bar. Atlas grammar G8 (event-triggered).
//
//   slot in bar -> horizontal position   (\sx -> \ex)
//   amp         -> mark size
~init = ~init <> {
	var folder = PathName("~/Music/cotf_samples/drums");

	group = Group.new;
	postf("loading samples : % \n", folder);
	~buffers = folder.entries.collect({|path,i|
		Buffer.read(s, path.fullPath, action:{|buf|
			postf("buffer alloc [%] \n", buf);
			if(folder.entries.size - 1 == i, { "samples loaded".postln });
		});
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \drumkitt2,
			\group, group,
			// derive slot from the clock so every bar restarts at drums[0]
			\bufnum, Pfunc{|e|
				var pos = (TempoClock.beats - phase).mod(barLen);
				var idx = (pos / dur).round.asInteger;
				bi = idx.mod(drums.size);
				~buffers[drums[bi]]
			},
			\octave, Pseq([5].stutter(24), inf),
			\start, 0,
			\note, Pseq([40], inf),
			\dur, dur,
			\pan, Pwhite(-0.05, 0.05),
			\attack, 0.02,
			\decay, 1,

			\type, \customVisualEvent,
			\shape, \circle,
			\sx, Pfunc{|e| (bi / (drums.size - 1)).linlin(0, 1, -0.8, 0.8) },
			\ex, Pkey(\sx),
			\startSize, Pfunc{|e| if(e.isRest, { 0 }, { (e[\amp] ? 0.5).linlin(0, 1, 40, 200) }) },
			\endSize, Pfunc{|e| if(e.isRest, { 0 }, { 10 }) },
			\startColor, Color.new(1.0, 0.5, 0.2),
			\endColor, Color.red.alpha_(0.0),
			\startWidth, 4,
			\endWidth, 0.5,
			\duration, 0.8,

			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant: [barLen, phase]);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\bufnum, ~buffers[0]);
	Pdef(m.ptn).pause;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (~buffers.notNil) {
			~buffers.do({|buf|
				buf.free;
				s.sync;
				postf("buffer dealloc [%] \n", buf);
			});
			~buffers = nil;
		};
	};
};

//------------------------------------------------------------
~next = {|d|
	var rate = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 0.5, 4, 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.5, 0.2, 1, 1);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\rate, rate);

	if (m.accelMassFiltered > 0.02, {
		if (Pdef(m.ptn).isPlaying.not, {
			Pdef(m.ptn).resume(quant: [barLen, phase]);
		});
	}, {
		if (Pdef(m.ptn).isPlaying, {
			Pdef(m.ptn).pause;
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = {|d,p| [m.accelMass, m.accelMassFiltered] };
