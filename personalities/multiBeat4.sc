/*
gestures:    [beat, shake]
description: A sample machine on the multiBeat grid. The dur mechanic is multiBeat1's, unchanged — every group fills exactly ONE beat, n notes of dur beat/n, built from Pswitch so the subdivision can only change at a group boundary. The ladder is binary only here, [1 2 4], because the point of this one is not the rhythm. The point is the palette: the kit is sorted by sample length at load, shortest first, and energy decides how far up that ladder the machine is allowed to reach. The kick is found by name and pinned to the downbeat whatever it sorts to, so there is always a floor under the beat. Still it is kick and nothing else; hard and it is drawing from the whole kit including the long rides, so the same beat turns from a dry tick into a wash. Weirdness comes from the rate, which is picked per note from a small ratio set, so notes drop an octave or double at random while the grid stays put.
sound:       cymbals1's kit through cymbals1's PlayBuf/RHPF/Compander voice, gated ADSR so the pattern owns the release
pitch:       none. \note is fixed; rate is the only pitch-like axis and it is deliberately erratic
rhythm:      n hits per beat, n from accel, ladder [1 2 4]. step 0 of every group is the accent and always the kick
instruments: [Template]
*/

var m = ~model;
var group;
var buffers;
var loaded = 0;
var kickIdx = 0;

// The downbeat wants the kick, and the kit is sorted by length so the
// kick is not at a known index. Find it by name once, at load.
var kickNames = ["drum", "kick", "bd"];

// THE RULE, multiBeat1's, verbatim.
//
//   Pn(n, n)          -> n copies of n : the dur denominator, latched for the group
//   Pseries(0, 1, n)  -> 0 .. n-1      : where we are inside the group
//
// Both are FINITE patterns of length n, so both Pswitches end their group
// on the same event and re-read \divIdx together. That lockstep is what
// keeps the count and the dur from disagreeing.
var beat = 0.5;
var divs = [1,4,8];

// The weirdness, in one place : what a hit is allowed to do to the sample
// rate. 1 is the sample as recorded; the rest are the machine misfiring.
// A negative rate is a reverse hit, and it has to start at the far end of
// the buffer or PlayBuf runs off frame 0 immediately and plays silence.
var rates = [1, 1, 1, 0.5, 2, 1.5, -1];

var kitFolder = "~/Downloads/openLabSamples/kit";

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// cymbals1's voice. It already has the gate and an ADSR, which is what a
// pattern needs — \sustain releases it and doneAction frees it.
SynthDef(\multiBeatKit, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0,
    attack=0.005, decay=0.1, sustain=0.8, release=0.02, gate=1, cutoff=50, rq=0.01|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.2],
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = RHPF.ar(sig, cutoff.clip(20, 8000), rq) * 2;
	sig = Compander.ar(sig, sig,
		thresh: -20.dbamp,
		slopeBelow: 1,
		slopeAbove: 0.5,
		clampTime: 0.01,
		relaxTime: 0.01
	);
	// sig = Balance2.ar(sig[0], sig[1], pan);
	Out.ar(out, sig * amp * env);
}).add;

//------------------------------------------------------------
// visual : the machine's own step grid. One lane per sample, ordered the
// way the palette ladder is ordered — short and dry at the bottom, the
// long rides at the top — so as energy opens the kit up the marks climb
// out of the low lanes and the widening palette is the picture. The
// downbeat always lands in the kick's own lane, so the beat has a fixed
// line in the grid to read against. x is the step within the beat, the
// same left-to-right traversal multiBeat1 and multiBeat3 use, so the
// three read as one score.
//
// Lineage: step-sequencer and punch-card notation, coloured the way the
// colour-field improvisation scores are — flat saturated fills, no grey.
// Atlas grammars G3 (grid / lattice) and G10 (machine-legible), palette
// row "colour-field improvisation" from §0.5.
//
//   step in group -> horizontal position   (\step -> \sx)
//   sample        -> lane, and hue         (\pick -> \sy, \startColor)
//   downbeat      -> the kick's lane, always
//   accent        -> size and weight       (\amp)
//   rate          -> tilt of the mark      (\rotation)
//   dur           -> how long the mark lives
~init = ~init <> {
	var folder = PathName(kitFolder);

	group = Group.new;

	postf("loading samples : % \n", kitFolder);

	// Sorted by length once they are all in, shortest first : that order
	// IS the palette ladder ~next climbs, and it is derived from the kit
	// rather than hardcoded, so dropping new samples into the folder
	// slots them in by character.
	buffers = folder.entries.collect({ |path, i|
		Buffer.read(s, path.fullPath, action: {|buf|
			loaded = loaded + 1;
			postf("buffer alloc [%] \n", buf);
			if(loaded == folder.entries.size, {
				buffers.sort({ |a, b| a.numFrames < b.numFrames });
				kickIdx = buffers.detectIndex({ |b|
					var name = PathName(b.path).fileName.toLower;
					kickNames.any({ |k| name.contains(k) });
				}) ? 0;
				postf("kit loaded, sorted short -> long. kick is lane % [%]\n",
					kickIdx, PathName(buffers[kickIdx].path).fileName);
			});
		});
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatKit,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,
			\note, 0,
			\octave, 5,

			// step 0 is the downbeat and always the kick, wherever it sorted
			// to; every other step draws from the first \pal of the ladder.
			\pick, Pfunc({ |e| if(e[\step] == 0, { kickIdx }, { (e[\pal] ? 1).rand }) }),
			\bufnum, Pfunc({ |e| buffers[e[\pick].clip(0, buffers.size - 1)] }),

			\amp, Pkey(\energy) * Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.55 }) }),
			\rate, Pfunc({ |e| rates.choose * (e[\roll] ? 1) }),
			\start, Pfunc({ |e|
				if(e[\rate] < 0, { 0.98 }, { [0, 0.12].wchoose([0.88, 0.12]) })
			}),
			\release, Pkey(\dur) * 1.2,
			\legato, 0.9,
			\pan, Pwhite(-0.4, 0.4),

			\type, \customVisualEvent,
			\shape, \square,
			\fill, true,
			\sx, (Pkey(\step) / Pkey(\div) * 1.5) - 0.75,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| e[\pick].linlin(0, (buffers.size - 1).max(1), 0.65, -0.65) }),
			\ey, Pkey(\sy),
			\rotation, Pfunc({ |e| e[\rate].abs.log2 * 0.25 }),
			\startSize, Pkey(\amp) * 90 + 10,
			\endSize, Pkey(\amp) * 20 + 4,
			\startColor, Pfunc({ |e|
				Color.hsv(e[\pick] / (buffers.size.max(1)), 1, 1, 0.85)
			}),
			\endColor, Pfunc({ |e|
				Color.hsv(e[\pick] / (buffers.size.max(1)), 1, 0.4, 0.0)
			}),
			\startWidth, (Pkey(\amp) * 8) + 1,
			\endWidth, 0.3,
			\duration, Pkey(\dur) * 2.5,

			\args, #[],
		)
	);

	// Seed the envir — ~next has not run when the first events fire, and a
	// nil \divIdx would index the Pswitch lists with nil.
	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\pal, 1);
	Pdef(m.ptn).set(\energy, 0);
	Pdef(m.ptn).set(\roll, 1);
	Pdef(m.ptn).set(\cutoff, 60);

	Pdef(m.ptn).play(quant: 1);
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
		buffers.do({ |buf|
			postf("buffer dealloc [%] \n", buf);
			buf.free;
		});
		buffers = nil;
	};
};

//------------------------------------------------------------
// Energy does two things and they are the piece : it climbs the
// subdivision ladder, and it opens the palette. \pal is how many of the
// length-sorted samples the machine may reach — 1 at rest, the whole kit
// flat out. Neither is a Pbind key, because a Pbind key would override
// the envir and defeat these .sets.
~next = {|d|
	var e = m.accelMassFiltered;
	var idx = e.lincurve(0, 3.0, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var pal = e.lincurve(0, 1.2, 1, buffers.size, 1)
		.round.asInteger.clip(1, buffers.size);
	var amp = e.lincurve(0, 1.0, -10, 0, -1);
	var roll = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.75, 1.25);
	var cutoff = ((d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5) * 2)
		.lincurve(-1.0, 1.0, 40, 900, 1);

	if(amp< 9.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\pal, pal);
	Pdef(m.ptn).set(\energy, amp.dbamp);
	Pdef(m.ptn).set(\roll, roll);
	Pdef(m.ptn).set(\cutoff, cutoff);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass, m.accelMassFiltered];
};
