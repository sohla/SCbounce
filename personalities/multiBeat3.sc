/*
gestures:    [beat, shake]
description: A companion voice for multiBeat1. It runs the same subdivision ladder — every group fills exactly ONE beat, so n notes of dur beat/n, and the whole thing is built from Pswitch so the grid can only change at a group boundary — but on a slower beat and a coarser ladder, and it does not choose its own key. multiBeat1 writes m.com.root on every note; this file reads it, so the two are always in the same harmony without either knowing about the other. Voice is treeWind's resonant bank rebuilt for pattern use: gated ASR env, freq/amp/pan/ffreq args, doneAction on release.
sound:       pink noise through a four ring DynKlank, stereo delay pair. gated so notes overlap and the bank keeps ringing under the next one
pitch:       a pool of degrees over m.com.root, so the root is multiBeat1's root
rhythm:      n notes per beat, n from accel, ladder [1 2 3] — a whole beat, halves, or the triplet
instruments: [Template]
*/

var m = ~model;
var group;

// THE RULE, the same one multiBeat1 states, on a longer beat.
//
//   Pn(n, n)             -> n copies of n      : the dur denominator, latched for the group
//   Pseries(0, 1, n)     -> 0 .. n-1           : where we are inside the group
//   Pseq(pool.keep(n),1) -> first n pool notes : the figure lengthens as it subdivides
//
// All three are FINITE patterns of length n, so all three Pswitches end
// their group on the same event and re-read \divIdx together.
//
// beat is 1.0 against multiBeat1's 0.5, so this voice moves at half its
// rate and the two ladders still land on the same grid.
var beat = 1.0;
var divs = [1, 2, 4, 8] * 2;
var pool = [0, 7, 12] + 36;   // needs at least divs.last entries, degrees over m.com.root

// Structure, not tunables : the ring bank the voice is built from. The
// SynthDef reads these directly, so there is one copy of the bank.
var ratios    = [1, 3.0, 5.01, 7.17];
var ringAmps  = [1, 0.2, 0.6, 0.15];
var ringTimes = [2.4, 1.6, 1.0, 0.7] * 0.1;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// treeWind's voice, made playable from a pattern : gate + Env.asr with
// doneAction, and every parameter the Pbind needs as an arg. The event
// system finds the gate arg and releases the note itself at \sustain.
SynthDef(\multiBeatWind, {|out=0, freq=220, amp=0.2, pan=0, gate=1,
    attack=0.02, decay=0.1, sustain=0.2,release=0.9, ffreq=4000|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var tone = SinOsc.ar(freq * [1,1.004] * 0.5 * SinOsc.ar(8).range(0.95, 1.05), 0, 0.2);
	var exc = BrownNoise.ar(0.03);
	var sig = DynKlank.ar(`[freq * ratios, ringAmps, ringTimes], exc) ;
	sig = LPF.ar(sig, ffreq.clip(80, 12000));
	sig = sig + DelayC.ar(sig, 0.03, [0.02, 0.027]) + tone;
	Out.ar(out, Pan2.ar(sig * env * amp, pan));
}).add;

//------------------------------------------------------------
// visual : register bands under multiBeat1's dots. Each note is one
// horizontal stroke — its half length is its dur, so a whole-beat note
// spans the canvas and a triplet leaves three short bars, and its height
// is the sounding pitch, so when multiBeat1 moves the root the whole band
// stack moves with it. Same left-to-right traversal as multiBeat1, so the
// two devices read as one score. Atlas grammars G4 (block / register
// band) over a G3 grid, traversal 6 (event-triggered). Phosphor palette,
// atlas §0.5.
//
//   step in group   -> horizontal position    (\step -> \sx)
//   note + root     -> register               (-> \sy)
//   dur             -> length of the band     (-> \startSize)
//   amp             -> stroke weight
//   accel (ffreq)   -> how much the band breathes  (\modulation)
~init = ~init <> {
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatWind,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx)),
			\octave, 3,
			\dur,  Pkey(\div).reciprocal * beat,
			\legato, 1.0,
			\attack, 0.06,
			\release, 1.2,
			\pan, Pwhite(-0.6, 0.6),

			\type, \customVisualEvent,
			\shape, \line,
			\numPoints, 24,
			\sx, (Pkey(\step) / Pkey(\div) * 1.5) - 0.75,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| (e[\note] + (e[\root] ? 0)).linlin(-3, 15, 0.6, -0.6) }),
			\ey, Pkey(\sy),
			\startSize, Pkey(\dur) * 320,
			\endSize, Pkey(\dur) * 320,
			\startColor, Color.new(0.49, 1.0, 0.63),
			\endColor, Color.new(0.05, 0.35, 0.15).alpha_(0.0),
			\startWidth, (Pkey(\amp) * 26) + 1,
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 3,
			\modulation, Pfunc({ |e|
				(freq: 0.4, amp: (e[\ffreq] ? 2000).linlin(700, 6000, 1, 14),
					harmonics: 2, type: \normal)
			}),

			\args, #[],
		)
	);

	// Seed the envir — ~next has not run when the first events fire, and a
	// nil \divIdx would index the Pswitch lists with nil.
	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\ffreq, 2000);
	Pdef(m.ptn).set(\root, 0);

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
// Accel drives the ladder index and the voice; the key comes from
// multiBeat1 through m.com.root. \divIdx and \root are deliberately NOT
// Pbind keys, because a Pbind key overrides the envir and defeats .set.
~next = {|d|
	var idx = m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -80, -17, -1);
	var ffreq = m.accelMassFiltered.lincurve(0, 2.0, 200, 6000, 2);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\root, m.com.root ? 0);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass, m.accelMassFiltered, (m.com.root ? 0) * 0.1];
};
