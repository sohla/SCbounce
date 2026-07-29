/*
gestures:    [beat, shake]
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

// Constants
var tickDt = 0.033;   // state ticks fire at ~30 Hz — one constant, no per-tick measurement

var applyTier;

// ------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

// ------------------------------------------------------------
SynthDef(\whisperVoice, {
	|out=0, freq=220, amp=0, gate=1, atk=0.5, rel=2.0, cutoff=1000|
	var env = EnvGen.kr(Env.asr(atk, 1, rel), gate, doneAction: 2);
	var sig = SinOsc.ar(freq, 0, 0.6) + Saw.ar(freq * 1.005, 0.15);
	var filt = RLPF.ar(sig, cutoff.lag(1.5), 0.4);
	Out.ar(out, filt ! 2 * env * amp.lag(0.1) * 0.3);
}).add;

// ------------------------------------------------------------
// Tier + maxAmp → per-voice amp. Engagement (read directly from
// file-scope) modulates cutoff — brighter as the arc advances.
applyTier = { |tier, maxAmp = 0.8|
	var amps = switch(tier,
		\low,  { [1, 0, 0] * 0.15 },
		\med,  { [1, 1, 0] * 0.20 },
		\high, { [1, 1, 1] * 0.30 }
	);
	var baseCutoff = switch(tier,
		\low,  { 400 },
		\med,  { 1200 },
		\high, { 4000 }
	);
	// Engagement multiplier — 1× at start, up to 3× at engagement=300+
	var engBoost = engagement.linexp(1, 300, 1, 3).clip(1, 3);
	if (voices.notNil, {
		voices.do({ |syn, i|
			syn.set(\amp, amps[i] * maxAmp, \cutoff, baseCutoff * engBoost);
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
	var activity = m.accelMassFiltered;
	var tier = case
		{ activity < 0.05 } { \low }
		{ activity < 0.5 }  { \med }
		{ true }            { \high };

	engagement = engagement + (activity * tickDt);
	if (activity > 0.05, {
		lastMotion = now;
		stillnessFired = false;
	});

	applyTier.(tier, 0.4);

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
			amp: 0.2, atk: 0.2, rel: 6.0, cutoff: 800,
			out: ob, group: group, type: \note,
		).play;
	});
};

~tuningNext = { |d, ctx|
	// Tuning ≠ engagement — don't accumulate. Voices bent via \ptch ramp.
	var tt = 20.0;
	var elapsed = TempoClock.beats - tuneTime;
	var ptch = if (elapsed < tt) {
		(elapsed / tt).linlin(0, 1, 0.7, 1.0)
	} { 1.0 };
	if (voices.notNil, {
		voices[0].set(\freq, 69.midicps * ptch, \amp, 0.15);
		voices[1].set(\amp, 0);
		voices[2].set(\amp, 0);
	});
};

~pieceNext = { |d, ctx|
	var activity = m.accelMassFiltered;
	var tier = case
		{ activity < 0.05 } { \low }
		{ activity < 0.5 }  { \med }
		{ true }            { \high };

	engagement = engagement + (activity * tickDt);
	if (activity > 0.05, {
		lastMotion = SystemClock.seconds;
		stillnessFired = false;
	});

	applyTier.(tier, 1.0);
};

~curtainNext = { |d, ctx|
	// Curtain caps at \med behaviour — no full triad in the outro.
	var activity = m.rrateMassFiltered;
	var effectiveTier = case
		{ activity < 0.05 } { \low }
		{ true }            { \med };
	var amps = switch(effectiveTier,
		\low, { [1, 0, 0] * 0.15 },
		\med, { [1, 1, 0] * 0.20 }
	);
	var cutoff = if (effectiveTier == \low) { 400 } { 1000 };

	engagement = engagement + (activity * tickDt);
	if (activity > 0.05, {
		lastMotion = SystemClock.seconds;
		stillnessFired = false;
	});

	if (voices.notNil, {
		voices.do({ |syn, i|
			syn.set(\amp, amps[i] * 0.3, \cutoff, cutoff);
		});
	});
};

// ------------------------------------------------------------
~onTick    = { |ctx| };
~onHalf    = { |ctx| };
~onBeat    = { |ctx|
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
~onBar     = { |ctx| };
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
~plotMin = 0;
~plotMax = 100;
~plot = { |d, p| [engagement, m.accelMassFiltered * 30] };
