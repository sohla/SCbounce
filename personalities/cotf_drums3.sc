/*
gestures:    [strike, tilt]
description: 4-bar Manchester/Madchester phrase at a uniform 16th grid, rests encoded as Rest(); nil slots skipped. Pattern runs while the player is moving and pauses when still.
sound:       Compressed drum-kit samples with FreeVerb tail; RLPF applied.
pitch:       None — sample kit. Rate driven by y-tilt.
rhythm:      64-slot phrase at dur=1, phase-locked.
instruments: [Gravitone]
*/

var m = ~model;
var lastTime = 0;
var bi = -1;
// music-downbeat alignment in clock beats
var phase = 4;
// bar length in clock beats. the conductor supplied this as
// ~scoreBeatsPerBar * ~scoreEventsPerBeat; standalone it is local.
var barLen = 16;
var group;

// legend: K=kick(0)  S=snare(5)  h=closed hat(2)  t=tom hi(7)  M=tom mid(9)  F=floor(13)  O=open hat(4)
// nil = rest slot (silence at that 16th)
var bar1 = [0, nil,  2,  nil,  5,  2,   0,  nil,  0,  nil,  2,  nil,  5,  2,  nil,  2];
var bar2 = bar1;
var bar3 = [0, nil,  2,  nil,  5,  2,   0,  0,    0,  nil,  2,  nil,  5,  2,  0,    2];
var bar4 = [0, nil,  2,  nil,  5,  2,   0,  nil,  9,  9,    7,  7,    13, 13, 4,    4];

var samples = bar2 ++ bar4 ++ bar3 ++ bar1;
var patternLen = samples.size;

~buffers;
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;

//------------------------------------------------------------
SynthDef(\drumkitt3, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0, freq=440,
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
// visual : one mark per hit, laid across the canvas by slot in the
// 64-step phrase — the grid becomes a horizontal staff, so the phrase
// reads left to right and resets every four bars. nil slots are rests
// and draw nothing, so the gaps are the groove.
// Atlas grammar G8 (event-triggered).
//
//   slot in phrase -> horizontal position   (\sx -> \ex)
//   amp            -> mark size
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
			\instrument, \drumkitt3,
			\group, group,
			// uniform 16th grid; nil slots → Rest() → event skipped but time advances
			\dur, 1,
			\bufnum, Pfunc{|e|
				var pos = (TempoClock.beats - phase).mod(patternLen);
				var slot = pos.floor.asInteger.mod(samples.size);
				var sm = samples[slot];
				bi = slot;
				if(sm.isNil, { Rest() }, { ~buffers[sm] })
			},
			\octave, Pseq([5].stutter(24), inf),
			\start, 0,
			\note, Pseq([40], inf),
			\pan, Pwhite(-0.05, 0.05),
			\attack, 0.02,
			\decay, 1,

			\type, \customVisualEvent,
			\shape, \circle,
			\sx, Pfunc{|e| (bi / (samples.size - 1)).linlin(0, 1, -0.9, 0.9) },
			\ex, Pkey(\sx),
			\startSize, Pfunc{|e| if(e.isRest, { 0 }, { (e[\amp] ? 0.5).linlin(0, 1, 30, 160) }) },
			\endSize, Pfunc{|e| if(e.isRest, { 0 }, { 8 }) },
			\startColor, Color.new(1.0, 0.5, 0.2),
			\endColor, Color.red.alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.5,
			\duration, 0.6,

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
