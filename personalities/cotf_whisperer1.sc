/*
gestures:    [beat, shake, twist, tilt]
description: Sustained ambient triad — three long-lived \whisperVoice synths (root / fifth / octave) held from ~init. Activity (m.accelMassFiltered) → tier (\low = root only, \med = root+fifth, \high = full triad). Engagement (per-load integral of activity × tickDt, accumulated in idle/piece/curtain — NOT tuning/silent) → DEPTH: filter opens brighter with more engagement. Hidden reveal: >10 s of stillness after non-\low activity fires a ghost echo of the last chord.
sound:       Warm sine + saw through slow LPF. Silent at rest. Immediate motion → tier response (root fades in, then fifth, then octave). Sustained engagement across a session → cutoff blossoms independently, brighter for the "seasoned" player.
pitch:       Root from ctx.voicePool.first wrapped to pitch class + baseMidi=60; fifth = root+7; octave = root+12. Refreshed on ~onBeat. \tuning locks to A4/E5/A5 with slow 20-s \ptch ramp.
rhythm:      None — three continuous drones. Ghost echo is the only discrete event.
instruments: [Aetherharp]
*/

// Uses recipes from concert_p_files.md:
//   §5  dedicated Group + idempotent ~deinit
//   §6  ~onResync (no Pdef — long-lived synths only)
//   §17 amp-curve palette
//   §23 tier from activity signal — m.accelMassFiltered read directly per tick, NO local accumulator
//   §25 multi-synthdef layering (three voices for triad)
//   §26 hidden-layer stillness reveal (ghost echo)
// Engagement-as-arc: one accumulator lives in each performing state's
// tick. Tuning + silent don't advance it. Section hooks read the
// absolute arc value for depth decisions.

var m = ~model;
var ob = ~outBus ? 0;
var group;
var voices;

// State: five vars total.
//   engagement     — per-load arc, accumulator advances in idle/piece/curtain
//   lastMotion     — SystemClock.seconds of last significant motion (stillness reveal)
//   stillnessFired — rearm gate for the ghost echo
//   lastRoot       — MIDI root memoized for the ghost echo's pitch
//   tuneTime       — TempoClock.beats captured on \tuning entry for the \ptch ramp
var engagement = 0;
var lastMotion = 0;
var stillnessFired = false;
var lastRoot = 69;
var tuneTime = 0;

// Gesture signals derived locally, per tick (the controller only publishes
// whole-body masses — accelMass = accelEvent.sumabs, rrateMass =
// rrateEvent.sumabs — so a single-axis one has to live here).
//   rrateXMassFiltered — |rrateEvent.x|, the RATE of twist about x.
//                        Always >= 0. Zero when held still at any angle.
//   tiltXFiltered      — gyroEvent.x folded to a signed -1..1, the ANGLE
//                        the stick is held at. Steady while held.
// Rate and angle are the same axis: how fast you're twisting vs where you
// ended up. Both are smoothed with the controller's asymmetric one-pole.
var rrateXMass = 0;
var rrateXMassFiltered = 0;
var rrateXAttack = 0.9;    // fast to rise on a twist
var rrateXDecay  = 0.3;    // slower to fall away
var tiltX = 0;
var tiltXFiltered = 0;
var tiltAttack = 0.5;      // symmetric — tilt is a position, not an impact
var tiltDecay  = 0.5;

// Constants
var tickDt = 0.033;   // state ticks fire at ~30 Hz — one constant, no per-tick measurement

var applyTier;
var smooth;
var updateGestures;
var voiceCutoff;

// ------------------------------------------------------------
// Same one-pole the controller uses for accelMass/rrateMass (~smooth in
// personalityController.scd) — kept local so this file is self-contained.
smooth = { |input, history, attack = 0.5, decay = 0.05|
	var coeff = attack;
	if (history > input, { coeff = decay });
	(coeff * input) + ((1 - coeff) * history)
};

// ------------------------------------------------------------
// Called first thing in EVERY state tick — the two signals are always
// current no matter which state the room is in.
updateGestures = { |d|
	rrateXMass = d.sensors.rrateEvent.x.abs;
	rrateXMassFiltered = smooth.(rrateXMass, rrateXMassFiltered, rrateXAttack, rrateXDecay);
	// fold(-0.5, 0.5) * 2 keeps the useful middle of the range and rescales
	// to a full signed -1..1 (the mapping ~pieceNext had inline).
	tiltX = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5) * 2;
	tiltXFiltered = smooth.(tiltX, tiltXFiltered, tiltAttack, tiltDecay);
};

// ------------------------------------------------------------
// One brightness algorithm for the whole file:
//   base   — where the state wants the filter (a tier cutoff, or a constant)
//   tilt   — WHERE the stick points scales it 0.4x .. 2.5x
//   twist  — HOW FAST it's rotating about x pushes it up to a further 3x
// Held still, twist contributes nothing and tilt alone colours the drone.
voiceCutoff = { |base = 1200|
	var tiltMul  = tiltXFiltered.lincurve(-1.0, 1.0, 0.3, 4.5, 3);
	var twistMul = 1;//rrateXMassFiltered.lincurve(0, 2.0, 1, 3, -2).clip(1, 3);
	(base * tiltMul * twistMul).clip(40, 7000)
};


// ------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

// ------------------------------------------------------------
SynthDef(\whisperVoice, {
	|out=0, freq=220, amp=0, gate=1, atk=0.5, rel=2.0, cutoff=1000, lagAttack=0.04, lagRelease=0.5|
	var env = EnvGen.kr(Env.asr(atk, 1, rel), gate, doneAction: 2);
	var sig = SinOsc.ar(freq, 0, 0.6) + Saw.ar(freq * 1.005, 0.15);
	var filt = RLPF.ar(sig, cutoff.lag(1.5), 0.4);
	Out.ar(out, filt ! 2 * env * amp.lagud(lagAttack, lagRelease) * 0.3);
}).add;

// ------------------------------------------------------------
// Tier + maxAmp → per-voice amp. Engagement (read directly from
// file-scope) modulates cutoff — brighter as the arc advances.
applyTier = { |tier, maxAmp = 0.8|
	var amps = switch(tier,
		\low,  { [1, 0, 0] * 0.9 },
		\med,  { [1, 0.5, 0] * 0.9 },
		\high, { [1, 0.5, 0.3] * 0.9 }
	);
	var baseCutoff = switch(tier,
		\low,  { 400 },
		\med,  { 1200 },
		\high, { 4000 }
	);
	// Engagement multiplier — 1× at start, up to 3× at engagement=200+
	var engBoost = engagement.lincurve(1, 200, 1, 3, -1).clip(1, 3);
	if (voices.notNil, {
		voices.do({ |syn, i|
			syn.set(\amp, amps[i] * maxAmp);
		});
	});
};

// ------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use {
		group = Group.new;
		voices = 3.collect({ |i|
			var defaults = [69, 76, 81];   // A4, E5, A5
			Synth(\whisperVoice, [
				\out, ob, \freq, defaults[i].midicps,
				\amp, 0, \atk, 0.5, \rel, 2.0, \cutoff, 800,
			], group);
		});
		~onResync = { |idx| };   // long-lived — nothing to rebuild
	};
};

// ------------------------------------------------------------
~deinit = ~deinit <> {
	fork {
		if (voices.notNil, {
			voices.do({ |syn| syn.set(\gate, 0) });
		});
		2.5.wait;
		if (group.notNil, {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		});
		voices = nil;
	};
};

// ------------------------------------------------------------
~onRoomState = { |ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  {
			tuneTime = TempoClock.beats;
			if (voices.notNil, {
				voices[0].set(\freq, 69.midicps);
				voices[1].set(\freq, 76.midicps);
				voices[2].set(\freq, 81.midicps);
			});
		},
		\piece,   { },
		\curtain, { },
		\silent,  {
			if (voices.notNil, { voices.do({ |syn| syn.set(\amp, 0) }) });
		}
	);
};

// ------------------------------------------------------------
// State ticks — each performing state accumulates engagement, derives
// tier from activity per tick, applies. Tuning does NOT accumulate
// (preparation, not performance). Silent has no tick.
// NO ~next block — all logic lives in the states it belongs to.

~idleNext = { |d, ctx|
	var now = SystemClock.seconds;
	var activity = m.accelMassFiltered.lincurve(0, 4, 0, 1, -1);
	var tier;
	updateGestures.(d);
	tier = case
		{ activity > 0.99 }  { \high }	
		{ activity > 0.7 }  { \med }	
		{ activity > 0.1 } { \low }
		{ true }            { \high };

	engagement = engagement + (activity * tickDt);
	if (activity > 0.05, {
		lastMotion = now;
		stillnessFired = false;
	});

	applyTier.(tier, m.accelMassFiltered.lincurve(0, 2.0, 0.0, 1.0, 1));

	if (voices.notNil, {
		var cutoff = voiceCutoff.(1200);
		voices[0].set(\freq, (69-12).midicps, \lagAttack, 0.2, \lagRelease, 0.2, \cutoff, cutoff);
		voices[1].set(\freq, (71-7).midicps, \lagAttack, 0.1,  \lagRelease, 2, \cutoff, cutoff);
		voices[2].set(\freq, (73).midicps, \lagAttack, 0.09,  \lagRelease, 4, \cutoff, cutoff);
	});


	// Ghost echo — stillness > 10 s after non-\low activity (§26).
	// engagement > 20 gate = player has done at least ~10-20 s of
	// moderate motion before the reveal can fire.
	if ((now - lastMotion) > 10
	    and: { stillnessFired.not }
	    and: { engagement > 20 }, {
		stillnessFired = true;
		(
			instrument: \whisperVoice,
			freq: lastRoot.midicps,
			amp: 0.2, atk: 0.8, rel: 6.0, cutoff: 200,
			out: ob, group: group, type: \note,
		).play;
	});
};

~tuningNext = { |d, ctx|
	// Tuning ≠ engagement — don't accumulate.
	// All three voices sound throughout, and the \ptch ramp bends all three
	// by the SAME ratio so the reference triad stays in tune with itself
	// while it bends. Accel drives the tier on the same thresholds as
	// ~idleNext / ~pieceNext, but here tier moves level + cutoff instead of
	// muting voices — nothing drops out of a tuning check (curtain-style
	// inline mapping rather than applyTier, which mutes by tier).
	var tt = 15.0;
	var elapsed = TempoClock.beats - tuneTime;
	var ptch = if (elapsed < tt) {
		(elapsed / tt).linlin(0, 1, 1.1, 1.0)
	} { 1.0 };
	var refTriad = [69-12, 71-7, 73];   // A4, E5, A5 — the tuning reference
	var activity = m.accelMassFiltered;
	var tier, amp, tierCutoff;
	updateGestures.(d);
	tier = case
		{ activity > 2.5 }  { \high }
		{ activity > 1.5 }  { \med }
		{ activity > 0.1 }  { \low }
		{ true }            { \high };
	amp = switch(tier,
		\low,  { 0.15 },
		\med,  { 0.25 },
		\high, { 0.35 }
	);
	// Tier still picks where the filter sits; tilt + twist scale it from there.
	tierCutoff = switch(tier,
		\low,  { 400 },
		\med,  { 1200 },
		\high, { 4000 }
	);


	if (voices.notNil, {
		var cutoff = voiceCutoff.(tierCutoff);
		voices.do({ |syn, i|
			syn.set(\freq, refTriad[i].midicps * ptch, \amp, amp, \cutoff, cutoff);
		});
		// Same staggered response as the other states.
		voices[0].set(\lagAttack, 0.02, \lagRelease, 0.02);
		voices[1].set(\lagAttack, 0.4,  \lagRelease, 0.5);
		voices[2].set(\lagAttack, 0.9,  \lagRelease, 1);
	});
};

~pieceNext = { |d, ctx|
	// Gesture mappings copied from ~idleNext: normalised activity, tier
	// thresholds, accel-driven maxAmp, per-voice lags, cutoff base.
	// Bottom tier case is \high, so a still seat sounds the full chord
	// rather than going quiet.
	// \freq stays OUT — ~onBar owns pitch during \piece (score voicePool →
	// root/fifth/octave), and this tick runs at 30 Hz, so idle's fixed
	// 69/71/73 cluster would clobber it outright.
	var now = SystemClock.seconds;
	var activity = m.accelMassFiltered.lincurve(0, 4, 0, 1, -1);
	var tier;
	updateGestures.(d);
	tier = case
		{ activity > 0.99 } { \high }
		{ activity > 0.7 }  { \med }
		{ activity > 0.1 }  { \low }
		{ true }            { \high };

	engagement = engagement + (activity * tickDt);
	if (activity > 0.05, {
		lastMotion = now;
		stillnessFired = false;
	});

	applyTier.(tier, m.accelMassFiltered.lincurve(0, 2.0, 0.0, 1.0, 1));

	if (voices.notNil, {
		var cutoff = voiceCutoff.(2000);
		voices[0].set(\lagAttack, 0.2,  \lagRelease, 0.2, \cutoff, cutoff);
		voices[1].set(\lagAttack, 0.1,  \lagRelease, 2, \cutoff, cutoff);
		voices[2].set(\lagAttack, 0.09, \lagRelease, 4, \cutoff, cutoff);
	});
};

~curtainNext = { |d, ctx|
	// Curtain caps at \med behaviour — no full triad in the outro.
	var activity = m.rrateMassFiltered;
	var effectiveTier, amps, tierCutoff;
	var amp = m.accelMassFiltered.lincurve(0, 2.0, 0.0, 1.0, 1);
	updateGestures.(d);
	effectiveTier = case
		{ activity < 0.05 } { \low }
		{ true }            { \med };
	amps = switch(effectiveTier,
		\low, { [1, 0, 0] * 0.15 },
		\med, { [1, 1, 0] * 0.20 }
	);
	tierCutoff = if (effectiveTier == \low) { 400 } { 1000 };

	engagement = engagement + (activity * tickDt);
	if (activity > 0.05, {
		lastMotion = SystemClock.seconds;
		stillnessFired = false;
	});

	if (voices.notNil, {
		var cutoff = voiceCutoff.(tierCutoff);
		voices.do({ |syn, i|
			syn.set(\amp, amps[i] * 0.3 * amp, \cutoff, cutoff);
		});
	});
};

// ------------------------------------------------------------
~onTick    = { |ctx| };
~onHalf    = { |ctx| };
~onBeat    = { |ctx|
};
~onBar     = { |ctx| 
	var root = (ctx.voicePool ? [69]).first.asInteger.wrap(0, 11) + 60;
	var fifth = (ctx.voicePool ? [69]).last.asInteger.wrap(0, 11) + 60;
	var octave = root + 12;
	if (voices.notNil and: { ~roomState != \tuning }, {
		s.bind {
			voices[0].set(\freq, root.midicps);
			voices[1].set(\freq, fifth.midicps);
			voices[2].set(\freq, octave.midicps);
		};
	});
	lastRoot = root;

};
~onPhrase  = { |ctx| };
~onSection = { |ctx|
	// Section boundary — read `engagement` here for any discrete
	// section-locked decisions. applyTier already reads engagement
	// continuously so cutoff brightening is automatic; this hook is
	// the site for future step-change reveals (e.g. unlock a fourth
	// voice above engagement 500).
};
~onChord   = { |ctx| };
~onKey     = { |ctx| };
~onScale   = { |ctx| };

// ------------------------------------------------------------
~plotMin = -1;
~plotMax = 3;
~plot = { |d, p|
// [m.rrateMassFiltered]
// [d.sensors.rrateEvent.x * 4]
	// raw x, the twist-rate mass built from it, and the tilt angle
	[d.sensors.rrateEvent.x, rrateXMassFiltered, tiltXFiltered]
};
