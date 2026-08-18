/*
gestures:    [beat, shake, tilt]
description: The orchestra's percussion section — bass drum, timpani, cymbals and shakers; move more and the pattern gets bigger, play longer and the fills come faster
internals:   Rhythmic backbone. Activity (m.accelMassFiltered) → tier (\low pulse / \med groove / \high busy), with hysteresis so a value sitting on a boundary can't flip at tick rate. Below restFloor the stick is only reading gravity, so it is silent. Engagement (per-load integral of activity × tickDt, accumulated in idle/piece/curtain — NOT tuning/silent) shrinks the fill interval, so seasoned playing gets denser fills. ~onSection swaps kit voicing per section. IDLE IS AN AUDITION RIG: previewMode makes it audible, idleLayer/idleSection pin a voicing, and y tilt solos one role group — set previewMode = false before the show.
sound:       Orchestral drum kit voiced 6 roles deep (kick / snare / hat / tom / cymbal / perc) plus a pitched timpani on its own SynthDef. Silent at rest; motion opens the tier.
pitch:       Percussive samples are unpitched. The timpani is not — \timpVoice plays two sources a fifth apart, each note picking the nearer one so the rate shift stays inside a minor third, tracking the chord root via ~onChord within one octave from timpRoot.
rhythm:      16-slot grid = TWO score bars (a bar is 8 events: ~scoreBeatsPerBar 2 × ~scoreEventsPerBeat 4). Show patterns low/med/high/fill_snare picked by tier; a separate dense `idle` pattern is the audition bed. Layer picked by section, fill frequency by engagement.
instruments: [brownShaker]
prints:      [brownShaker, blackShaker, brownBall]
seats:       [1, 2, 3, 4]
affinity:    [drums, drumming, percussion, timpani, thunder, storm, battle, marching, heartbeat, rhythm, beat, loud, big, war]
register:    [traditional]
family:      percussion
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

/*
- quiet moments go into half time
- compressor
*/

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
var currentTier = \low;
var barCounter = 0;
var parsedPatterns;

var tickDt = 0.033;

//------------------------------------------------------------
// IDLE PREVIEW — idle doubles as the audition rig. Edit + save to hear
// a change; the file reloads whole.
//
// >>> SET previewMode = false BEFORE THE SHOW. <<<
// True means the kit plays through walk-in and the audience hears it.
// accelMass is accelEvent.sumabs * 0.33, so a STATIONARY stick still
// reads ~0.3 from gravity — it never returns to 0. Anything below this
// is "not moving" and must be silent, or the pattern plays forever.
// Tune by watching `act` in the post window while holding still.
var restFloor = 0.2;

var previewMode = true;
var idleLayer   = nil;   // pin one of layerOrder;   nil = fall to idleSection
var idleSection = nil;   // pin one of sectionOrder; nil = \default
var lastPreviewLabel = "";

// ============================================================
// ROLES
// ============================================================
var roles = [\kick, \snare, \hat, \tom, \cymbal, \perc];

// Idle audition: y tilt picks one of these groups and only it sounds.
// activeRoles nil = every role plays, which is the show behaviour.
var roleGroups = [
	[\kick, \tom],
	[\snare],
	[\hat, \cymbal],
	[\perc],
];
var activeRoles = nil;

// ============================================================
// LAYERS — per-section kit voicings (§26 hidden reveal)
//
// Each role is a COLLECTION of sample indices; one is picked per hit,
// so a role varies instead of repeating the same sample. A bare integer
// still works — it's read as a one-element list.
//
// Indices are positions in the orchkit folder listing. ~init posts the
// real index->name map on load ("[perc] orchkit map") — trust that, not
// a shell `ls`: pathMatch globs case-insensitively, so Tamb sorts before
// TG1. Adding a sample renumbers everything after it. An out-of-range
// index is wrapped, never nil, so a typo here can't kill the pattern.
// ============================================================
// Index map for the 22-file folder (2026-07-31), verified against sclang.
// glob sorts CASE-INSENSITIVELY — Tamb < Tambo < TG1, not ASCII order.
//   0 TimpaniA   1 TimpaniE   2 BE_a-due  3 Claves   4 CloseKick
//   5 DirtyKick  6 GTr1       7 HB1trem   8 Splash   9 PTr
//  10 Roto1     11 Roto2     12 Roto4    13 Shaker  14 SmallMetal2
//  15 Tamb      16 Clap3     17 Clap4    18 TG1mu   19 TG1si
//  20 TiKast-l  21 TiKast-r
var layers = (
	default: (kick: [4],       snare: [14,2],       hat: [13,3,14],      tom: [15,16],        cymbal: [18,19],   perc: [15]),
	intro:   (kick: [],         snare: [],          hat: [13,20,21],        tom: [10,11],           cymbal: [],   perc: [13]),   // soft
	dev:     (kick: [6,9,5,4],     snare: [12,7,16],     hat: [11,13,14],     tom: [10,11,12,0,1], cymbal: [8],   perc: [3,18]), // tom-heavy
	recap:   (kick: [6,9,4,5],   snare: [9,7,8,16,17], hat: [18,13,14],     tom: [10,11,0,1],    cymbal: [2,8], perc: [21,20]),// full concert
	coda:    (kick: [6,4],         snare: [13],          hat: [19],           tom: [10],           cymbal: [7],   perc: [20]),   // soft again
);

// Valid idleLayer values. Musical order, soft -> full, not alphabetical.
var layerOrder = [\intro, \coda, \default, \dev, \recap];

//------------------------------------------------------------
// TIMPANI — pitched, so it runs on \timpVoice instead of the kit player
// and sits outside the roles loop. Two sources a fifth apart; each note
// picks the nearer one, so the rate shift never exceeds a minor third.
//
//   srcMidi   what the sample actually SOUNDS — set by ear, the 60/67
//             in the filenames are the library's numbering, not pitch
//   timpRoot  bottom of the playable octave; notes wrap into root..root+12
var timpEnabled = true;
var timpSamples = [
	(idx: 0, srcMidi: 45),   // 60 Timpani Accent In A.aif  — A2?
	(idx: 1, srcMidi: 40),   // 67 Timpani Accent In E.aif  — E2?
];
var timpRoot = 40;
var timpNote = 40;   // current pitch, refreshed from the score in ~onChord

// ------------------------------------------------------------
// VOLUME BALANCE
// ------------------------------------------------------------
// Per-sample trim, keyed by FILE NAME rather than index so it survives
// the folder being re-sorted or added to. ~init resolves these to a
// per-index array once. Anything not listed defaults to 1.0.
// The orchkit library is mixed-dynamic (ff piatti next to pp triangle),
// so this is where you flatten it out. Tune by ear — edit and save,
// the mtime auto-reload picks it up immediately.
var sampleGain = (
	'60 Timpani Accent In A':     1.00,   // timpani, pitched A — UNTRIMMED
	'67 Timpani Accent In E':     1.00,   // timpani, pitched E — UNTRIMMED
	'BE_a-due_20Is_mf':           0.70,   // bass drum a due, mf — long (19.6s)
	'Claves-Fibre2_ES1':          0.70,   // claves — sharp transient
	'Close Kick':                 0.30,   // kick — UNTRIMMED
	'Dirty Kick':                 0.50,   // kick — UNTRIMMED
	'GTr1_ES-mufa_mf1':           0.98,   // gran tamburo, mf
	'HB1-L_trem_wool_mf_8s':      0.60,   // wool tremolo roll — sustained (6.2s)
	'HBE12Z-SplashTr-Sch_f_R-na': 0.90,   // splash, f — hottest in the kit
	'PTr_ES-L1_ff':               0.60,   // piccolo triangle, ff
	'Roto_ES_f_1_L1':             0.55,   // rototom 1, f
	'Roto_ES_f_2_L1':             0.75,   // rototom 2, f
	'Roto_ES_f_4_L1':             0.85,   // rototom 4, f
	'Shaker-Chrom_ES1':           1.00,   // shaker
	'Small Metal 2':              1.00,   // small metal — UNTRIMMED
	'Tamb_ES_f_1':                0.70,   // tambourine, f
	'Tambo Clap 3':               1.00,   // clap — UNTRIMMED
	'Tambo Clap 4':               1.00,   // clap — UNTRIMMED
	'TG1_ES_di_pp_mu_1':          0.40,   // triangle pp muted — already quiet
	'TG1_ES_di_pp_si_1':          0.50,   // triangle pp ringing (12.8s)
	'Ti-Kast_ES-l1':              0.90,
	'Ti-Kast_ES-r1':              0.90,
);

// Per-role balance — the kit's internal mix, independent of which
// sample a role currently uses. Multiplies on top of sampleGain.
var roleGain = (kick: 1.0, snare: 0.9, hat: 0.5, tom: 0.9, cymbal: 0.6, perc: 0.8);

// Resolved in ~init: sampleGain looked up per buffer index.
var gainByIdx;

// Symbol keys, NOT strings. Event is an IdentityDictionary, so a String
// key can never match ctx.sectionId (a different String instance) — the
// lookup silently returned nil and this swap had never once fired.
var sectionToLayer = (
	intro:  \intro,
	'A':    \default,
	dev:    \dev,
	'B':    \default,
	recap:  \recap,
	coda:   \coda,
);

// Valid idleSection values, in score order.
var sectionOrder = [\intro, 'A', \dev, 'B', \recap, \coda];

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

// Grid length, in clock beats. The score's bar is only
// ~scoreBeatsPerBar * ~scoreEventsPerBeat = 8 events, so a 16-slot grid
// spans TWO bars. Keep this a multiple of that 8 or the cycle drifts
// off the downbeat.
var patternLen = 16;

// PARKED — the ticks still assign this but the Pbind no longer reads it.
// Driving \dur and the slot lookup from it teleported the pattern to a
// new grid position on every rate change (beats*rate), and the ticks
// re-evaluate at 30 Hz. Needs bar-quantised switching + hysteresis before
// it goes back in.
var patternRate = 1;

// `timp` is a role like any other here — it just gets played by
// \timpVoice at timpNote instead of by the kit player.
var rawPatterns = (
	// idle — the audition bed. EVERY role busy, so whichever group the y
	// tilt selects always has something to play. Not used in the show.
	idle: (
		kick:   "K . . . k . . . K . . . k . . .",
		snare:  ". . . . S . . s . . . . S . s .",
		hat:    "h . h h . h h . h . h h . h h .",
		tom:    ". . t . . . t . . t . . t . . t",
		cymbal: "C . . . c . . . C . . . c . . .",
		perc:   ". p . . . . p . . p . . . . p .",
		// timp:   "T . . . . . . . T . . . . . . .",
	),
	// low — doubled: every event that was one per 16 is now one per 8.
	low: (
		snare:  "S . . . . . . s S . . . . . . s",
		hat: "C . c . C . c . C . c . C . c .",
		perc:   ". p . . . . p . . p . . . . p .",
	),
	med: (
		kick:  "K . . . . . . . k . . . K . . .",
		snare: ". . s . . . . . . . . . S . . .",
		hat:   ". . h . . . h . . . h . . . h .",
	),
	high: (
		kick:  "K . . . k . . . k . . . K . . .",
		snare: ". . . . S . . . . . . . S . . .",
		hat:   "h h h h h h h h h h h h h h h h",
		cymbal: "C . . . . . c . . . . . c . c .",
	),
	fill_snare: (
		snare:  "S s s S s s S s S s s S s s S S",
		// cymbal: "C . . . . . . . . . . . . . . .",
		// timp:   "T . . . . . . . . . . . T . . .",
	),
);

// ============================================================
// Fast attack, SLOW decay — an envelope follower. The coefficient is the
// weight on the new sample, so a high decay means no smoothing and the
// tier chases every peak and trough of the raw signal. §9 range: 0.2-0.7.
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.35;

// ============================================================
SynthDef(\percHit, {
	|bufnum=0, out=0, amp=0.5, rate=1, pan=0,
	 attack=0.01, decay=0.01, sustain=0.3, release=2.2, gate=1, cutoff=14000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0], startPos: 0, loop: 0) * env;
	sig = RLPF.ar(sig, cutoff, rq);
	Out.ar(out, sig * amp);
}).add;

// ============================================================
// Pitched timpani. \rate is the shift from the source sample's own
// pitch — the caller works it out, this just plays it.
SynthDef(\timpVoice, {
	|bufnum=0, out=0, amp=0.5, rate=1, pan=0,
	 attack=0.002, release=3.0, curve=(-4), cutoff=6000, rq=0.8|
	var lr  = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.perc(attack, release, 1, curve), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: 0, loop: 0) * env;
	sig = RLPF.ar(sig, cutoff, rq);
	Out.ar(out, Balance2.ar(sig[0], sig[1], pan) * amp);
}).add;

// ============================================================
~init = ~init <> {
	var folder = PathName("~/Music/cotf_samples/orchkit");

	parsedPatterns = rawPatterns.collect({|pat|
		pat.collect({|str| parseGrid.(str) })
	});

	topEnvironment.use {
		var entries = folder.entries;
		group = Group.new;
		postf("loading samples : % \n", folder);

		// Resolve the name-keyed sampleGain table to a per-index array,
		// and post the real index->name map so the indices used in
		// `layers` can be checked against what SC actually loaded.
		gainByIdx = entries.collect({|path| sampleGain[path.fileNameWithoutExtension.asSymbol] ? 1.0 });
		"[perc] orchkit map (index : file : gain)".postln;
		entries.do({|path, i|
			postf("  %  %  x%\n", i.asString.padLeft(2), path.fileNameWithoutExtension, gainByIdx[i]);
		});

		bufs = entries.collect({|path, i|
			var buf = Buffer.read(s, path.fullPath, action:{|b|
				postf("buffer alloc [% : %] \n", i, path.fileNameWithoutExtension);
			});
			buf
		});

		Event.addEventType(eventTypeName, {|e|
			var slot = ~slotIdx;
			// Fall back rather than go silent — an unmapped section or a
			// mistyped key must never mute a seat mid-show.
			var pat = parsedPatterns[currentPatternKey] ? parsedPatterns[\low];
			var layer = layers[currentLayerKey] ? layers[\default];
			var base = ~baseAmp ? 0;

			if (base > 0.001 and: { pat.notNil } and: { layer.notNil } and: { bufs.notNil }, {
				roles.do({|role|
					var grid = pat[role];
					var choices = layer[role];
					var inGroup = activeRoles.isNil or: { activeRoles.includes(role) };
					if (inGroup and: { grid.notNil } and: { choices.notNil }, {
						var v = grid.wrapAt(slot);
						if (v > 0, {
							// Role holds a COLLECTION of samples — pick one per
							// hit so repeats vary. asArray keeps a bare integer
							// working; wrapAt keeps a bad index from ever being
							// nil (a throw here would kill the Pdef for good).
							var bufIdx = choices.asArray.choose;
							var buf    = bufIdx !? { bufs.wrapAt(bufIdx) };
							if (buf.notNil, {
								(
									instrument: \percHit,
									bufnum:     buf,
									out:        ob,
									group:      group,
									amp:        v * base
									            * ((gainByIdx !? { gainByIdx.wrapAt(bufIdx) }) ? 1.0)
									            * (roleGain[role] ? 1.0),
									pan:        -0.08.rrand(0.08),
									attack:     0.004,
									release:    2.0,
									type:       \note,
								).play;
							});
						});
					});
				});

				// Pitched timpani — own grid row, own SynthDef, nearest
				// source sample shifted to timpNote.
				if (timpEnabled, {
					var grid = pat[\timp];
					var v    = grid !? { grid.wrapAt(slot) } ? 0;
					if (v > 0, {
						var src = timpSamples.minItem({|t| (timpNote - t.srcMidi).abs });
						var buf = bufs.wrapAt(src.idx);
						if (buf.notNil, {
							(
								instrument: \timpVoice,
								bufnum:     buf,
								out:        ob,
								group:      group,
								rate:       (timpNote - src.srcMidi).midiratio,
								amp:        v * base
								            * ((gainByIdx !? { gainByIdx.wrapAt(src.idx) }) ? 1.0),
								pan:        -0.05.rrand(0.05),
								release:    rrand(2.0, 4.0),
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
					var pos = (~beatClock.beats - phase).mod(patternLen);
					pos.round.asInteger.mod(patternLen)
				},
			)
		);

		Pdef(m.ptn).set(\baseAmp, 0);
		Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
	};

	// MUST be outside topEnvironment.use — the conductor dispatches this
	// as d.env.use { ~onResync.(idx) }, so it has to live in the DEVICE
	// env. Assigned inside the use block it lands in topEnvironment, the
	// device keeps the controller's empty default, and the Pdef is never
	// restarted across the beat clock's re-anchor.
	~onResync = { |idx|
		topEnvironment.use {
			postf("[perc] onResync idx % clockBeats % \n",
				idx, ~beatClock.beats.round(0.01));
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
		};
	};
};

// ============================================================
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	fork {
		if (group.notNil) {
			// s.bind { group.freeAll };
			group.free;
			s.sync;
			group = nil;
		};
		if (bufs.notNil) {
			bufs.do({|buf| buf.free; s.sync });
			bufs = nil;
		};
	};
};

// ============================================================
// Leaving idle drops the audition gate — the show plays the whole kit.
~onRoomState = { |ctx|
	if (ctx.state != \idle, { activeRoles = nil });
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

//------------------------------------------------------------
// Idle doubles as the preview rig — pinned by idleLayer / idleSection
// at the top of the file. Arc accumulates either way.
~idleNext = { |d, ctx|
	var activity = m.accelMassFiltered;
	var label;

	engagement = engagement + (activity * tickDt);

	if (previewMode.not, {
		Pdef(m.ptn).set(\baseAmp, 0);   // show behaviour — silent at rest
	}, {
		if (idleLayer.notNil, {
			currentLayerKey = idleLayer;
			label = "layer %".format(idleLayer);
		}, {
			var sect = idleSection !? { idleSection.asSymbol };
			currentLayerKey = (sect !? { sectionToLayer[sect] }) ? \default;
			label = "section % -> layer %".format(sect, currentLayerKey);
		});
		
		// currentLayerKey = sectionToLayer[\coda];

		// y tilt picks the role group — nod down through the kit:
		// kick+tom, snare, hat+cymbal, perc. Pattern keeps playing; the
		// roles outside the group just don't sound.
		activeRoles = roleGroups[
			m.gyroYFiltered.clip(-1, 1)
				.linlin(-1, 1, 0, roleGroups.size - 1)
				.round.asInteger.clip(0, roleGroups.size - 1)
		];
		label = label ++ "  roles " ++ activeRoles.join("+");

		// One dense bed for auditioning — activity drives amp and speed,
		// not density.
		currentPatternKey = \idle;
		patternRate = if (activity > 0.8, { 1 }, { 0.5 });
		//
		// currentPatternKey = \low;


		// Silent below restFloor, then jump to an audible level — the
		// handler gates on base > 0.001, so a curve starting at 0 leaves
		// a dead band just above the threshold.
		Pdef(m.ptn).set(\baseAmp,
			if (activity > restFloor,
				{ activity.lincurve(restFloor, 2.0, 0.05, 0.5, -3) },
				{ 0 }
			)
		);

		// post on change only — 30 Hz unconditional is unusable
		label = label ++ "  pattern " ++ currentPatternKey;
		if (label != lastPreviewLabel, {
			lastPreviewLabel = label;
			("[perc idle] " ++ label).postln;
		});
	});
};

~tuningNext = { |d, ctx|
	// Tuning ≠ engagement — don't accumulate.
	// Pdef(m.ptn).set(\baseAmp, 0);
	~pieceNext.(d, ctx);  // reuse pieceNext for engagement accumulation
};

//------------------------------------------------------------
// Gesture mappings as tuned in ~idleNext. Layer comes from ~onSection
// here rather than a pin, and \high can swap in a fill.
~pieceNext = { |d, ctx|
	var activity = m.accelMassFiltered.linlin(0, 3.0, 0, 1.5);
	// Hysteresis — separate rise and fall thresholds, so a value sitting
	// on a boundary can't flip tier at 30 Hz. Climbs at 0.60 / 1.10,
	// drops back at 0.45 / 0.85.
	var tier = switch(currentTier,
		\low,  { if (activity > 0.60) { \med } { \low } },
		\med,  { if (activity > 1.10) { \high }
		         { if (activity < 0.45) { \low } { \med } } },
		\high, { if (activity < 0.85) { \med } { \high } }
	);
	// engagement shrinks the fill interval: 8 bars early, 2 well into the arc
	var fillEvery = engagement.linlin(0, 300, 8, 2).round.max(2).asInteger;

	currentTier = tier;
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
	// currentPatternKey = \med;

	patternRate = 1;//if (tier == \high, { 2 }, { 1 });

	// Same restFloor as ~idleNext — below it the stick is only reading
	// gravity, so it must be silent.
	Pdef(m.ptn).set(\baseAmp,
		if (activity > restFloor,
			{ activity.lincurve(restFloor, 2.0, 0.05, 0.7, -3) },
			{ 0 }
		)
	);

	// TEMP diagnostic — posts only when the resolved selection changes.
	block {
		var tag = "%/%/rate%".format(currentPatternKey, currentLayerKey, patternRate);
		if (tag != lastPreviewLabel, {
			lastPreviewLabel = tag;
			("[perc piece] " ++ tag ++ "  act " ++ activity.round(0.01)).postln;
		});
	};
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
	// .asSymbol is load-bearing: sectionToLayer is an Event, i.e. an
	// IdentityDictionary, so a String key never matches ctx.sectionId.
	var key = ctx.sectionId !? { sectionToLayer[ctx.sectionId.asSymbol] };
	if (key.notNil, {
		currentLayerKey = key;
	});
	barCounter = 0;
	("[perc] section % engagement %".format(ctx.sectionId, engagement.round(0.1))).postln;
};

~onChord   = { |ctx|
	// Timpani follows the chord root, wrapped into its one playable octave.
	timpNote = timpRoot + (ctx.voicePool ? [69]).first.asInteger.mod(12);
};
~onKey     = { |ctx| };
~onScale   = { |ctx| };

// ============================================================
~plotMin = 0;
~plotMax = 100;
~plot = { |d, p| [m.accelMassFiltered] };
