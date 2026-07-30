/*
gestures:    [beat, shake, twist, tilt]
description: Sustained ambient triad — three long-lived \whisperVoice synths (root / fifth / octave) held from ~init. Activity (m.accelMassFiltered, normalised 0..1) → tier, INVERTED: held still the chord opens to \high (full triad), and motion collapses it toward \low (root only). Tier weights are [1, 0, 0] / [1, 0.5, 0] / [1, 0.5, 0.3]. Brightness comes from gesture, not tier — m.gyroXFiltered (roll tilt) sets cutoff, mapped per state. Engagement (per-load integral of activity × tickDt, accumulated in idle/piece/curtain — NOT tuning/silent) is still measured but currently drives nothing. Hidden reveal: >10 s of stillness after non-\low activity fires a ghost echo of the last chord (idle only).
sound:       Warm sine + saw through slow LPF. Full chord at rest, thinning to the root as the player moves. Per-voice lag times are staggered so voice 0 tracks the gesture and voices 1–2 bloom and decay behind it.
pitch:       Root from ctx.voicePool.first wrapped to pitch class + baseMidi=60; fifth = voicePool.last; octave = root+12. Refreshed on ~onBar. \tuning locks to A3/E4/A4 with a 15 s ramp applied to \freq (there is no \ptch control here), entering slightly sharp (1.1) and settling to true.
rhythm:      None — three continuous drones. Ghost echo is the only discrete event.
instruments: [boneRod]
*/

// Recipes: §5 group + ~deinit, §6 ~onResync, §17 amp palette,
// §23 tier from activity, §25 multi-voice layering, §26 stillness reveal.

var m = ~model;
var ob = ~outBus ? 0;
var group;
var voices;

var engagement = 0;          // per-load arc, advances in idle/piece/curtain
var lastMotion = 0;          // SystemClock.seconds of last motion
var stillnessFired = false;  // rearm gate for the ghost echo
var lastRoot = 69;           // memoized for the ghost echo's pitch
var tuneTime = 0;
var tickDt = 0.033;          // ticks fire at ~30 Hz

var applyTier;


//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\whisperVoice, {
	|out=0, freq=220, amp=0, gate=1, atk=0.5, rel=2.0, cutoff=1000, lagAttack=0.04, lagRelease=0.5|
	var env = EnvGen.kr(Env.asr(atk, 1, rel), gate, doneAction: 2);
	var sig = SinOsc.ar(freq, 0, 0.6) + Saw.ar(freq * 1.005, 0.15);
	var filt = RLPF.ar(sig, cutoff.lag(1.5), 0.4);
	Out.ar(out, filt ! 2 * env * amp.lagud(lagAttack, lagRelease) * 0.3);
}).add;

//------------------------------------------------------------
// tier + maxAmp → per-voice amp
applyTier = { |tier, maxAmp = 0.8|
	var amps = switch(tier,
		\low,  { [1, 0, 0] * 0.9 },
		\med,  { [1, 0.5, 0] * 0.9 },
		\high, { [1, 0.5, 0.3] * 0.9 }
	);
	var baseCutoff = switch(tier,   // unused — cutoff is per-state now
		\low,  { 400 },
		\med,  { 1200 },
		\high, { 4000 }
	);
	var engBoost = engagement.lincurve(1, 200, 1, 3, -1).clip(1, 3);   // unused
	if (voices.notNil, {
		voices.do({ |syn, i|
			syn.set(\amp, amps[i] * maxAmp);
		});
	});
};

//------------------------------------------------------------
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

//------------------------------------------------------------
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

//------------------------------------------------------------
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

//------------------------------------------------------------
// Tuning does NOT accumulate engagement. Silent has no tick.

~idleNext = { |d, ctx|
	var now = SystemClock.seconds;
	var activity = m.accelMassFiltered.lincurve(0, 4, 0, 1, -1);
	var tier = case
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
		var cutoff = (m.gyroXFiltered.fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 360, 5400, 3);
		voices[0].set(\freq, (69-12).midicps, \lagAttack, 0.2, \lagRelease, 0.2, \cutoff, cutoff);
		voices[1].set(\freq, (71-7).midicps, \lagAttack, 0.1,  \lagRelease, 2, \cutoff, cutoff);
		voices[2].set(\freq, (73).midicps, \lagAttack, 0.09,  \lagRelease, 4, \cutoff, cutoff);
	});

	// ghost echo (§26)
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

//------------------------------------------------------------
// All three voices sound throughout — tier moves level + cutoff, never
// mutes, so nothing drops out of a check. Ramp enters sharp, one ratio
// across the triad so it stays in tune with itself.

~tuningNext = { |d, ctx|
	var tt = 15.0;
	var elapsed = TempoClock.beats - tuneTime;
	var ptch = if (elapsed < tt) { (elapsed / tt).linlin(0, 1, 1.1, 1.0) } { 1.0 };
	var refTriad = [69-12, 71-7, 73];
	var activity = m.accelMassFiltered;
	var tier = case
		{ activity > 2.5 } { \high }
		{ activity > 1.5 } { \med }
		{ activity > 0.1 } { \low }
		{ true }           { \high };
	var amp        = switch(tier, \low, { 0.15 }, \med, { 0.25 }, \high, { 0.35 });
	var tierCutoff = switch(tier, \low, { 400 },  \med, { 1200 }, \high, { 4000 });

	if (voices.notNil, {
		var cutoff = (tierCutoff * (m.gyroXFiltered.fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 0.3, 4.5, 3)).clip(40, 7000);
		var level  = amp * m.accelMassFiltered.lincurve(0, 2.0, 0.0, 1.0, 1);
		voices.do({ |syn, i|
			syn.set(\freq, refTriad[i].midicps * ptch, \amp, level, \cutoff, cutoff);
		});
		voices[0].set(\lagAttack, 0.02, \lagRelease, 0.02);
		voices[1].set(\lagAttack, 0.4,  \lagRelease, 0.5);
		voices[2].set(\lagAttack, 0.9,  \lagRelease, 1);
	});
};

//------------------------------------------------------------
// \freq stays out — ~onBar owns pitch here, and this tick would clobber it.

~pieceNext = { |d, ctx|
	var now = SystemClock.seconds;
	var activity = m.accelMassFiltered.lincurve(0, 4, 0, 1, -1);
	var tier = case
		{ activity > 0.99 } { \high }
		{ activity > 0.7 }  { \med }
		{ activity > 0.1 }  { \low }
		{ true }            { \high };

	engagement = engagement + (activity * tickDt);
	if (activity > 0.05, {
		lastMotion = now;
		stillnessFired = false;
	});

	applyTier.(tier, m.accelMassFiltered.lincurve(0, 2.0, 0.0, 2.0, 1));

	if (voices.notNil, {
		var cutoff = (m.gyroXFiltered.fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 600, 9000, 3);
		voices[0].set(\lagAttack, 0.2,  \lagRelease, 0.2, \cutoff, cutoff);
		voices[1].set(\lagAttack, 0.1,  \lagRelease, 2, \cutoff, cutoff);
		voices[2].set(\lagAttack, 0.09, \lagRelease, 4, \cutoff, cutoff);
	});
};

//------------------------------------------------------------
// Caps at \med — no full triad in the outro.

~curtainNext = { |d, ctx|
	var activity = m.rrateMassFiltered;
	var effectiveTier = case
		{ activity < 0.05 } { \low }
		{ true }            { \med };
	var amps = switch(effectiveTier,
		\low, { [1, 0, 0] * 0.15 },
		\med, { [1, 1, 0] * 0.20 }
	);
	var tierCutoff = if (effectiveTier == \low) { 400 } { 1000 };
	var amp = m.accelMassFiltered.lincurve(0, 2.0, 0.0, 1.0, 1);

	engagement = engagement + (activity * tickDt);
	if (activity > 0.05, {
		lastMotion = SystemClock.seconds;
		stillnessFired = false;
	});

	if (voices.notNil, {
		var cutoff = (tierCutoff * (m.gyroXFiltered.fold(-0.5, 0.5) * 2).lincurve(-1.0, 1.0, 0.3, 4.5, 3)).clip(40, 7000);
		voices.do({ |syn, i|
			syn.set(\amp, amps[i] * 0.3 * amp, \cutoff, cutoff);
		});
	});
};

//------------------------------------------------------------
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
~onSection = { |ctx| };   // site for engagement step-change reveals
~onChord   = { |ctx| };
~onKey     = { |ctx| };
~onScale   = { |ctx| };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 3;
~plot = { |d, p|
	[d.sensors.rrateEvent.x, m.rrateXMassFiltered, m.gyroXFiltered]
};
