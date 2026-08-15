/*
gestures:    [beat, shake, twist, tilt]
description: Sustained ambient triad — three long-lived \whisperVoice synths (root / fifth / octave) held from ~init. Activity (m.accelMassFiltered, normalised 0..1) → tier, INVERTED: held still the chord opens to \high (full triad), and motion collapses it toward \low (root only). Tier weights are [1, 0, 0] / [1, 0.5, 0] / [1, 0.5, 0.3]. Brightness comes from gesture, not tier — m.gyroXFiltered (roll tilt) sets cutoff. Engagement (per-load integral of activity × tickDt) is still measured but currently drives nothing.
sound:       Warm sine + saw through slow LPF. Full chord at rest, thinning to the root as the player moves. Per-voice lag times are staggered so voice 0 tracks the gesture and voices 1–2 bloom and decay behind it.
pitch:       Root from a local voice pool wrapped to pitch class + baseMidi=60; fifth = pool.last; octave = root+12. Refreshed on a slow timer.
rhythm:      None — three continuous drones.
instruments: [boneRod]
*/

/*
- needs more pitch content
- needs a pattern
- does the ghost code even work / remove it
*/

var m = ~model;
var group;
var voices;

var engagement = 0;          // per-load arc
var lastMotion = 0;          // SystemClock.seconds of last motion
var stillnessFired = false;  // rearm gate for the ghost echo
var lastRoot = 69;           // memoized for the ghost echo's pitch
var tickDt = 0.033;          // ticks fire at ~30 Hz
var barTime = 0;             // last pitch refresh
var baseMidi = 60;

// the conductor used to hand pitch material down as ctx.voicePool.
// standalone, the pool is local.
var voicePool = [0, 4, 7];

var applyTier;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
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
	var baseCutoff = switch(tier,   // unused — cutoff is gesture-driven now
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
	group = Group.new;
	voices = 3.collect({ |i|
		var defaults = [69, 76, 81];   // A4, E5, A5
		Synth(\whisperVoice, [
			\freq, defaults[i].midicps,
			\amp, 0, \atk, 0.5, \rel, 2.0, \cutoff, 800,
		], group);
	});
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
~curveAbove = { |in, thresh = 0.2, inMax = 1.0, outMin = 0.3, outMax = 1.0, curve = -4|
	if (in < thresh) { 0 } {
		in.lincurve(thresh, inMax, outMin, outMax, curve)
	}
};

//------------------------------------------------------------
// visual : one mark per pitch refresh. three continuous drones give the
// score nothing discrete to mark, so the chord change is the traversal.
// Atlas grammar G8 (event-triggered).
//
//   pitch refresh -> a mark appears
//   tier          -> mark size (the fuller the chord, the wider)
~next = { |d|
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

	if (TempoClock.beats > (barTime + 4), {
		var root   = voicePool.first.asInteger.wrap(0, 11) + baseMidi;
		var fifth  = voicePool.last.asInteger.wrap(0, 11) + baseMidi;
		var octave = root + 12;
		barTime = TempoClock.beats;
		if (voices.notNil, {
			s.bind {
				voices[0].set(\freq, root.midicps);
				voices[1].set(\freq, fifth.midicps);
				voices[2].set(\freq, octave.midicps);
			};
		});
		lastRoot = root;

		(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
			shape: \circle,
			startSize: switch(tier, \low, { 80 }, \med, { 160 }, \high, { 260 }),
			endSize: 20,
			startColor: Color.new(0.6, 0.8, 1.0), endColor: Color.blue.alpha_(0.0),
			startWidth: 3, endWidth: 0.5,
			duration: 3.0).play;
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 3;
~plot = { |d, p|
	[d.sensors.rrateEvent.x, m.rrateXMassFiltered, m.gyroXFiltered]
};
