/*
gestures:    [beat, shake]
description: multiBeat1 with the group gate relaxed. The beat is still sacred — every beat sums to exactly 1 — but the dur is no longer locked for the whole group. If accel falls away mid-beat the figure is allowed to coarsen into the remaining time: 0.25, 0.25, 0.5 instead of four stubborn 0.25s. Pconst owns the beat; it hands out the dur accel currently wants until one more would overrun, then hands out exactly the remainder and starts the next beat. Family (binary vs triplet) IS still latched per beat, because that is what stops a beat mixing 1/4 and 1/3 and landing the remainder on a 24th.
sound:       short filtered pulse+sine mallet tone; release scales with dur so long notes ring and fast ones stay tight
pitch:       pool cycles continuously — the beat length is emergent here, so the "first n notes" rule of multiBeat1 no longer applies
rhythm:      dur free to change every note, beat sum locked to 1. binary beats move on 1/8s, triplet beats on 1/6s
instruments: [Template]
*/

var m = ~model;
var group;

// THE BEAT. Pconst pulls a dur off the source pattern each event and
// yields it, until one more would take the running sum past 1 — then it
// yields exactly (1 - elapsed) and ends. So the source can change every
// single event and the beat still closes on the grid.
//
// The source is a Pswitch over the family's durs, read fresh per event,
// so accel is felt on the very next note rather than at the next beat.
var binDurs  = [1, 1/2, 1/4, 1/8] * 0.8;
var tripDurs = [1/3, 1/6] * 0.8;

var binBeat  = Pconst(1, Pswitch(binDurs,  Pkey(\binIdx)));
var tripBeat = Pconst(1, Pswitch(tripDurs, Pkey(\tripIdx)));

// THE FAMILY, latched per beat. The outer Pswitch reads \famIdx once,
// then embeds a whole Pconst beat before reading it again — the same
// group-gate mechanism multiBeat1 used, but now gating only the family,
// not the dur. This is load-bearing: let a beat mix a 1/4 and a 1/3 and
// the remainder lands on 1/24, which hits the beat boundary but sits on
// no musical grid at all.
//
// Which family each density band asks for, coarse -> fine.
// Set every entry to 0 for a binary-only ladder.
var famLadder = [0, 0, 1, 0, 1, 0];

var pool = [0, 4, 8, 11, 12, 11, 8, 4];   // needs at least divs.last entries

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeatVoice2, {|out=0, freq=440, amp=0.2, pan=0,
    attack=0.005, release=0.4, ffreq=3000|
	var env = EnvGen.kr(Env.perc(attack, release), doneAction: 2);
	var sig = Pulse.ar(freq, 0.3, 0.5) + SinOsc.ar(freq / 2, 0, 0.8);
	sig = RLPF.ar(sig, ffreq.clip(80, 12000), 0.4);
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

//------------------------------------------------------------
// visual : the beat as a ruler. Each mark sits at its own phase within
// the beat and its weight is its dur, so a beat that starts fine and
// coarsens reads as marks that thin out and then thicken as they cross
// the canvas. The beat boundary is always the left edge.
// Atlas grammar G8 (event-triggered) over a G3 grid field.
//
//   phase in beat -> horizontal position   (\sx -> \ex)
//   dur           -> stroke weight, and how long the mark lives
//   amp           -> mark size
~init = ~init <> {
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatVoice2,
			\group, group,

			\dur,  Pswitch([binBeat, tripBeat], Pkey(\famIdx)),
			\note, Pseq(pool, inf),
			\octave, 4,
			\release, Pkey(\dur) * 1.8,
			\pan, Pwhite(-0.2, 0.2),

			\type, \customVisualEvent,
				\shape, \circle,
			\sx, Pfunc({ (TempoClock.beats.frac * 1.6) - 0.8 }),
			\ex, Pkey(\sx),
			\startSize, Pkey(\amp) * 400,
			\endSize, 8,
			\startColor, Color.new(0.6, 1.0, 0.8),
			\endColor, Color.new(0.1, 0.4, 0.6).alpha_(0.0),
			\startWidth, (Pkey(\dur) * 6) + 1,
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 2,

			\args, #[],
		)
	);

	// Seed the envir — ~next has not run when the first events fire, and
	// a nil index would reach Pswitch.wrapAt as nil.
	Pdef(m.ptn).set(\famIdx, 0);
	Pdef(m.ptn).set(\binIdx, 0);
	Pdef(m.ptn).set(\tripIdx, 0);
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
// One density figure from accel drives all three indices. Both families
// get a density-matched rung every tick, so whichever one the beat has
// latched always has a sensible dur waiting for it — even while accel
// is sitting on the other family's band.
~next = {|d|
	var dens = m.accelMassFiltered.lincurve(0, 2.5, 0, 1, 1).clip(0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -12, -1);
	var ffreq = m.accelMassFiltered.lincurve(0, 2.0, 700, 6000, 2);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\famIdx,  famLadder.at((dens * (famLadder.size - 1)).round.asInteger));
	Pdef(m.ptn).set(\binIdx,  (dens * (binDurs.size  - 1)).round.asInteger);
	Pdef(m.ptn).set(\tripIdx, (dens * (tripDurs.size - 1)).round.asInteger);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ffreq, ffreq);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass, m.accelMassFiltered];
};
