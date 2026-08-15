/*
gestures:    [beat, shake]
description: A subdivision ladder driven by accel. Every group fills exactly ONE beat, so the note count and the dur are two views of the same number: n notes of dur 1/n. Accel picks n off the ladder [1 2 3 4 6 8] — 3 and 6 are the triplets. Because the whole thing is built from Pswitch, the subdivision can only change at a group boundary, so the grid never drifts mid-figure however hard the stick is moved.
sound:       short filtered pulse+sine mallet tone; release scales with dur so fast subdivisions stay tight and slow ones ring
pitch:       first n notes of a local pool — the figure literally lengthens as it subdivides
rhythm:      n notes per beat, n from accel. binary 1/2/4/8 and triplet 3/6 on one continuous ladder
instruments: [Template]
*/

var m = ~model;
var group;

// THE RULE, in one line each.
//
// divs is the ladder of subdivisions, ordered by density so accel can
// ride straight up it. For each n:
//
//   Pn(n, n)             -> n copies of n      : the dur denominator, latched for the group
//   Pseries(0, 1, n)     -> 0 .. n-1           : where we are inside the group
//   Pseq(pool.keep(n),1) -> first n pool notes : "each subdivision has that multiple of values"
//
// All three are FINITE patterns of length n, so all three Pswitches end
// their group on the same event and re-read \divIdx together. That
// lockstep is what keeps the figure coherent — drop it and the note
// count and the dur can disagree.
//
// Take 3 and 6 out of divs for a binary-only ladder.
var divs = [2, 4, 6];
var pool = [0, 4, 7, 11, 12, 11, 7, 2];   // needs at least divs.last entries

var divPat  = Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx));
var stepPat = Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx));
var notePat = Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx));

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeatVoice, {|out=0, freq=440, amp=0.2, pan=0,
    attack=0.005, release=0.4, ffreq=3000|
	var env = EnvGen.kr(Env.perc(attack, release), doneAction: 2);
	var sig = Pulse.ar(freq, 0.3, 0.5) + SinOsc.ar(freq / 2, 0, 0.6);
	sig = RLPF.ar(sig, ffreq.clip(80, 12000), 0.4);
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

//------------------------------------------------------------
// visual : the subdivision, drawn as itself. Each group lays n marks
// left to right across the canvas over one beat, so the density you
// hear is the density you see, and a triplet reads as three marks where
// a sixteenth run reads as four. Atlas grammar G8 (event-triggered)
// over a G3 grid field.
//
//   step in group -> horizontal position   (\sx -> \ex)
//   amp           -> mark size
//   dur           -> how long the mark lives
~init = ~init <> {
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatVoice,
			\group, group,

			\div,  divPat,
			\step, stepPat,
			\note, notePat,
			\octave, Prand([4,5,6], inf),
			\dur,  Pkey(\div).reciprocal * 0.5,
			// \release, Pkey(\dur) * 1.8,
			\pan, Pwhite(-0.2, 0.2),

			\type, \customVisualEvent,
			\shape, \circle,
			\sx, (Pkey(\step) / Pkey(\div) * 1.6) - 0.8,
			\ex, Pkey(\sx),
			\startSize, Pkey(\amp) * 400,
			\endSize, 8,
			\startColor, Color.new(0.6, 1.0, 0.8),
			\endColor, Color.new(0.1, 0.4, 0.6).alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 2,

			\args, #[],
		)
	);

	// Seed the envir — ~next has not run when the first events fire, and
	// a nil \divIdx would index the Pswitch lists with nil.
	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\ffreq, 2000);

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
	};
};

//------------------------------------------------------------
// Accel drives the ladder index, and nothing else touches it — \divIdx
// is deliberately NOT a Pbind key, because a Pbind key would override
// the envir and defeat this .set.
~next = {|d|
	var idx = m.accelMassFiltered.lincurve(0, 2.5, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -60, -12, -1);
	var ffreq = m.accelMassFiltered.lincurve(0, 2.0, 700, 6000, 2);
	var rel = m.accelMassFiltered.lincurve(0, 2.0, 0.1, 1.2, 2);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\release, rel);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass, m.accelMassFiltered];
};
