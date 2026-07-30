/*
gestures:    [beat, shake, tilt]
description: Rhythmic backbone. Activity (m.accelMassFiltered) → tier (\low = pulse, \med = groove, \high = double-time). Engagement (per-load integral of activity × tickDt, accumulated in idle/piece/curtain — NOT tuning/silent) → DEPTH: fill interval shrinks with engagement, so seasoned playing gets denser fills. Hidden reveal: ~onSection silently swaps kit voicing per section (intro / A / dev / B / recap / coda).
sound:       Orchestral drum kit voiced 6 roles deep (kick / snare / hat / tom / cymbal / perc). Silent at rest. Immediate motion → pattern tier. Sustained engagement → fills come more often.
pitch:       None — percussive samples from ~/Music/cotf_samples/orchkit.
rhythm:      16-slot bar. Pattern picked by tier; layer picked by section. Fill frequency modulated by engagement (arc).
instruments: [Gravitone]
*/

// Uses recipes from concert_p_files.md:
//   §5  dedicated Group + idempotent ~deinit
//   §6  state-aware ~onResync
//   §17 amp palette
//   §20 layered running-Pdef + gestural one-shots (fill-insertion analog)
//   §23 tier from activity signal — read m.accelMassFiltered directly per tick
//   §26 hidden-layer section-swap (kit voicing rotation)
// Engagement-as-arc: one accumulator in each performing state's tick.
// Tuning + silent don't advance it. Engagement modulates fillEvery
// (fills get more frequent as the piece unfolds).

var m = ~model;
var ob = ~outBus ? 0;
var group;
var bufs;
var eventTypeName = (\percEvent_ ++ m.ptn).asSymbol;
var phase = 0;

// State: five vars total (only engagement is new; the rest support
// the event handler and pattern lookups).
//   engagement       — per-load arc, accumulated in idle/piece/curtain
//   currentLayerKey  — orchkit voicing selector, set by ~onSection
//   currentPatternKey — pattern selector, set per tick from tier + fill schedule
//   barCounter       — count of bars since last section reset (for fill scheduling)
//   parsedPatterns   — pre-parsed grids, loaded once in ~init
var engagement = 0;
var currentLayerKey = \default;
var currentPatternKey = \low;
var barCounter = 0;
var parsedPatterns;

var tickDt = 0.033;

// ============================================================
// ROLES
// ============================================================
var roles = [\kick, \snare, \hat, \tom, \cymbal, \perc];

// ============================================================
// LAYERS — per-section kit voicings (§26 hidden reveal)
// ============================================================
var layers = (
	default: (kick: 6, snare: 5, hat: 9,  tom: 7, cymbal: 4, perc: 10),
	intro:   (kick: 2, snare: 9, hat: 11, tom: 6, cymbal: 3, perc: 13),  // soft
	dev:     (kick: 2, snare: 8, hat: 7,  tom: 6, cymbal: 4, perc: 1),   // tom-heavy
	recap:   (kick: 2, snare: 5, hat: 10, tom: 6, cymbal: 0, perc: 14),  // full concert
	coda:    (kick: 2, snare: 9, hat: 11, tom: 6, cymbal: 3, perc: 13),  // soft again
);

var sectionToLayer = (
	"intro":  \intro,
	"A":      \default,
	"dev":    \dev,
	"B":      \default,
	"recap":  \recap,
	"coda":   \coda,
);

// ============================================================
// PATTERN DSL — tier-driven grooves + one fill
// ============================================================
var parseGrid = {|str|
	var chars = Array.newFrom(str.select({|c|
		c.isAlpha or: { c.isDecDigit } or: { c == $. } or: { c == $X }
	}));
	Array.fill(chars.size, {|i|
		var c = chars[i];
		case
		{ c == $. }        { 0 }
		{ c == $X }        { 1.2 }
		{ c.isDecDigit }   { c.digit * 0.1 }
		{ c.isUpper }      { 1.0 }
		{ c.isLower }      { 0.5 }
		{ true }           { 0 }
	})
};

var rawPatterns = (
	low: (
		kick: "K . . . . . . . . . . . . . . .",
	),
	med: (
		kick:  "K . . . . . . . K . . . . . . .",
		snare: ". . . . S . . . . . . . S . . .",
		hat:   ". . h . . . h . . . h . . . h .",
	),
	high: (
		kick:  "K . . . K . . . K . . . K . . .",
		snare: ". . . . S . . . . . . . S . . .",
		hat:   "h h h h h h h h h h h h h h h h",
	),
	fill_snare: (
		snare:  "S s s S s s S s S s s S s s S S",
		cymbal: "C . . . . . . . . . . . . . . .",
	),
);

// ============================================================
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;

// ============================================================
SynthDef(\percHit, {
	|bufnum=0, out=0, amp=0.5, rate=1, pan=0,
	 attack=0.01, decay=0.01, sustain=0.3, release=1.2, gate=1, cutoff=14000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0], startPos: 0, loop: 0) * env;
	sig = RLPF.ar(sig, cutoff, rq);
	Out.ar(out, sig * amp);
}).add;

// ============================================================
~init = ~init <> {
	var folder = PathName("~/Music/cotf_samples/orchkit");

	parsedPatterns = rawPatterns.collect({|pat|
		pat.collect({|str| parseGrid.(str) })
	});

	topEnvironment.use {
		group = Group.new;
		postf("loading samples : % \n", folder);
		bufs = folder.entries.collect({|path, i|
			var buf = Buffer.read(s, path.fullPath, action:{|b|
				postf("buffer alloc [% : %] \n", i, path.fileNameWithoutExtension);
			});
			buf
		});

		Event.addEventType(eventTypeName, {|e|
			var slot = ~slotIdx;
			var pat = parsedPatterns[currentPatternKey];
			var layer = layers[currentLayerKey];
			var base = ~baseAmp ? 0;

			if (base > 0.001 and: { pat.notNil } and: { layer.notNil } and: { bufs.notNil }, {
				roles.do({|role|
					var grid = pat[role];
					var bufIdx = layer[role];
					if (grid.notNil and: { bufIdx.notNil }, {
						var v = grid.wrapAt(slot);
						if (v > 0, {
							(
								instrument: \percHit,
								bufnum:     bufs[bufIdx],
								out:        ob,
								group:      group,
								amp:        v * base,
								pan:        -0.08.rrand(0.08),
								attack:     0.01,
								release:    1.0,
								type:       \note,
							).play;
						});
					});
				});
			});
		});

		Pdef(m.ptn,
			Pbind(
				\type,    eventTypeName,
				\dur,     1,
				\slotIdx, Pfunc {|e|
					var barLen = ~scoreBeatsPerBar * ~scoreEventsPerBeat;
					var pos = (~beatClock.beats - phase).mod(barLen);
					pos.round.asInteger.mod(barLen)
				},
			)
		);

		Pdef(m.ptn).set(\baseAmp, 0);
		Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);

		~onResync = { |idx|
			topEnvironment.use {
				Pdef(m.ptn).stop;
				s.bind { group.freeAll };
				Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
			};
		};
	};
};

// ============================================================
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (bufs.notNil) {
			bufs.do({|buf| buf.free; s.sync });
			bufs = nil;
		};
	};
};

// ============================================================
~onRoomState = { |ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { Pdef(m.ptn).set(\baseAmp, 0) },
		\piece,   { },
		\curtain, { },
		\silent,  { Pdef(m.ptn).set(\baseAmp, 0) },
	);
};

// ============================================================
// State ticks — each performing state derives tier from activity per
// tick, accumulates engagement, picks pattern. Tuning + silent don't
// accumulate. NO ~next block.

~idleNext = { |d, ctx|
	// Idle: silent output (baseAmp=0), but arc still accumulates from
	// any device motion during idle exploration.
	var activity = m.accelMassFiltered;
	engagement = engagement + (activity * tickDt);
	Pdef(m.ptn).set(\baseAmp, 0);
};

~tuningNext = { |d, ctx|
	// Tuning ≠ engagement — don't accumulate.
	Pdef(m.ptn).set(\baseAmp, 0);
};

~pieceNext = { |d, ctx|
	var activity = m.accelMassFiltered;
	var deadZone = 0.15;
	var amp = if (activity > deadZone,
		{ activity.lincurve(deadZone, 2.0, 0.0, 1.0, -1) },   // §17 expressive
		{ 0 }
	);
	var tier = case
		{ activity < 0.05 } { \low }
		{ activity < 0.8 }  { \med }
		{ true }            { \high };
	// Engagement modulates fill frequency: early piece = fills every
	// 8 bars, well into the arc = fills every 2 bars. Only affects
	// \high tier where fills exist.
	var fillEvery = engagement.linlin(0, 300, 8, 2).round.max(2).asInteger;

	engagement = engagement + (activity * tickDt);

	currentPatternKey = switch(tier,
		\low,  { \low },
		\med,  { \med },
		\high, {
			if (barCounter.mod(fillEvery) == 0 and: { barCounter > 0 },
				{ \fill_snare },
				{ \high })
		}
	);

	Pdef(m.ptn).set(\baseAmp, amp);
};

~curtainNext = { |d, ctx|
	// Curtain: pulse only regardless of tier. Faded amp curve.
	var activity = m.rrateMassFiltered;
	var deadZone = 0.12;
	var amp = if (activity > deadZone,
		{ activity.lincurve(deadZone, 1.0, 0.0, 0.15, -4) },   // §17 faded
		{ 0 }
	);
	engagement = engagement + (activity * tickDt);
	Pdef(m.ptn).set(\baseAmp, amp);
	currentPatternKey = \low;
};

// ============================================================
~onTick    = { |ctx| };
~onHalf    = { |ctx| };
~onBeat    = { |ctx| };
~onBar     = { |ctx|
	// Bar counter drives fill scheduling — read by ~pieceNext.
	barCounter = barCounter + 1;
};
~onPhrase  = { |ctx| };

// Section change — silently swap kit voicing (§26). Section hook is
// also the site to read arc position for section-locked decisions,
// but for now the kit-swap is the main compositional gesture here.
~onSection = { |ctx|
	var key = sectionToLayer[ctx.sectionId];
	if (key.notNil, {
		currentLayerKey = key;
	});
	barCounter = 0;
	("[perc] section % engagement %".format(ctx.sectionId, engagement.round(0.1))).postln;
};

~onChord   = { |ctx| };
~onKey     = { |ctx| };
~onScale   = { |ctx| };

// ============================================================
~plotMin = 0;
~plotMax = 100;
~plot = { |d, p| [engagement, m.accelMassFiltered * 30] };
