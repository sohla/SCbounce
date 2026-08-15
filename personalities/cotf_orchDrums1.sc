/*
gestures:    [beat, tilt, shake]
description: Multi-role, multi-hit orchestral drum kit. Per-role pattern grids (string DSL) played simultaneously per 16th-note slot. Layers (low/mid/high) select instrument VOICING; patterns (grooves + fills) select RHYTHM. gyroY → layer band; a bar counter swaps in a fill every ~fillEvery bars. accelMassFiltered scales base amp AND gates ghost hits (soft grid values drop out when still).
sound:       Full orchestral kit voiced 6 roles deep (kick / snare / hat / tom / cymbal / perc). Voicing shifts by tilt band. Fills every N bars.
pitch:       None — percussive samples.
rhythm:      16 slots per bar (16th-note resolution). Each role's grid decides which slots hit and how loud. Multi-hit slots (kick+hat together) work by design.
instruments: [Gravitone]
*/

var m = ~model;
var group;
var bufs;   // file-scope so event handler resolves it lexically
            // (would be nil via `~buffers` because those run in d.env / event env)

// Per-env event type — Event.addEventType is a global registry, so a
// unique name per personality avoids cross-file clobber.
var eventTypeName = (\drumEvent_ ++ m.ptn).asSymbol;
var phase = 0;
// bar length in clock beats. the conductor supplied this as
// ~scoreBeatsPerBar * ~scoreEventsPerBeat; standalone it is local.
var barLen = 16;

// ============================================================
// SAMPLE DESCRIPTIONS
// ============================================================
// The orchkit folder holds 15 cryptically-named samples. Indices match
// folder.entries load order (post window confirms on load).
var sampleDesc = [
	"BE_a-due_20Is_mf           — crash cymbal",             //  0
	"Claves-Fibre2_ES1          — clave, single wood block", //  1
	"GTr1_ES-mufa_mf1           — sub low concert bass drum",//  2
	"HB1-L_trem_wool_mf_8s      — cymbal tremelo",           //  3
	"HBE12Z-SplashTr-Sch_f_R-na — cymbal splash",            //  4
	"PTr_ES-L1_ff               — snare",                    //  5
	"Roto_ES_f_1_L1             — roto tom low",             //  6
	"Roto_ES_f_2_L1             — roto tom mid",             //  7
	"Roto_ES_f_4_L1             — roto tom high",            //  8
	"Shaker-Chrom_ES1           — shaker",                   //  9
	"Tamb_ES_f_1                — tamberine",                // 10
	"TG1_ES_di_pp_mu_1          — triangle muted",           // 11
	"TG1_ES_di_pp_si_1          — triangle ring",            // 12
	"Ti-Kast_ES-l1              — Kastanet 1",               // 13
	"Ti-Kast_ES-r1              — Kastanet 2",               // 14
];

// ============================================================
// ROLES — musical functions the patterns speak in
// ============================================================
// Patterns and layers both key off these symbols. Silence for a role at
// a slot = grid value 0 OR role missing from the current layer.
var roles = [\kick, \snare, \hat, \tom, \cymbal, \perc];

// ============================================================
// LAYERS — role → orchkit sample index, per tilt band
// ============================================================
// gyroY (pitch tilt) selects the band. Any role can be omitted from a
// layer to keep that layer sparse (e.g. "high" has no kick → shimmer).
var layers = (
	low: (
		kick: 2, snare: 6, hat: 9, tom: 7, cymbal: 0, perc: 1
	),
	mid: (
		kick: 6, snare: 5, hat: 9, tom: 7, cymbal: 4, perc: 10
	),
	high: (
		kick: 8, snare: 8, hat: 10, tom: 12, cymbal: 4, perc: 13
	),
);

// ============================================================
// PATTERN DSL PARSER
// ============================================================
// Grid chars, read left-to-right (spaces / bar-lines ignored):
//   .         rest        → 0
//   X         accent      → 1.2
//   K S H T C P (upper)   → full hit, amp 1.0
//   k s h t c p (lower)   → ghost hit, amp 0.5
//   1..9      explicit    → amp 0.1..0.9
// The letters have no semantic meaning per role — they're a mnemonic
// for whoever reads the grid ("K" in the \kick row = a kick hit).
// Length of each parsed grid = number of non-ignored chars = slots.
// Use 16 chars per bar for 16th-note grids (project convention).
var parseGrid = {|str|
	// .select on a String returns a String; .collect on a String tries
	// to accumulate into another String and blows up in _ArrayAdd when
	// the collect fn returns a Number. Force an explicit Array via
	// Array.newFrom + manual iteration so there's no ambiguity about
	// the accumulator class regardless of SC version.
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

// ============================================================
// PATTERNS — role → grid string
// ============================================================
// Each pattern is an event (role → 16-slot string). Missing role =
// silent for that role. Uppercase letter = full hit; lowercase = ghost;
// X = accent. Layout convention: group by 4 slots for readability.
// Grooves and fills live in the same dict — swapping is just picking
// a different key.
var rawPatterns = (

	// -------- grooves --------

	march: (
		// classic K S K S with a hat 'and'-of-beat pulse and a
		// downbeat crash
		kick:   "K . . . . . . . K . . . . . . .",
		snare:  ". . . . S . . . . . . . S . . .",
		hat:    ". . h . . . h . . . h . . . h .",
		cymbal: "C . . . . . . . . . . . . . . .",
	),

	rock8: (
		// standard 8th-note rock groove — hat 8ths, kick on 1 & 'and-of-3'
		kick:   "K . . . . . K . . . . . K . . .",
		snare:  ". . . . S . . . . . . . S . . .",
		hat:    "h . H . h . H . h . H . h . H .",
	),

	tribal: (
		// tom-heavy loop with perc filigree; no kick/snare
		tom:    "T . t . T . . t T . t . T . . t",
		perc:   ". . p . . . p . p . . . p . p .",
	),

	march_wide: (
		// half-time, spacious
		kick:   "K . . . . . . . . . . . K . . .",
		cymbal: "C . . . . . . . . . . . . . . .",
		perc:   ". . . . . . . . p . . . . . . .",
	),

	// -------- fills (swapped in every ~fillEvery bars) --------

	fill_snare: (
		// snare roll with accents on 1, 5, 9, 13
		snare:  "S s s S s s S s S s s S s s S S",
	),

	fill_toms: (
		// tom cascade, punctuated by a crash on the downbeat
		tom:    "T t T T t T t T t T T T T t T T",
		cymbal: "C . . . . . . . . . . . . . . .",
	),
);

var fillEvery = 4;   // every Nth bar, swap groove → fill

// ============================================================
// RUNTIME STATE
// ============================================================
// The conductor used to pick groove/fill per room-state and per score
// section. Standalone there is neither, so the pair is fixed here —
// change these two to re-voice the whole kit.
var currentLayer   = \mid;
var currentGroove  = \rock8;
var currentFill    = \fill_snare;
var currentPattern = \rock8;
var barCounter     = 0;
var barTime        = 0;
var parsedPatterns;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.98;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\drumkitOrch, {|bufnum=0, out, amp=0.5, rate=1, start=0, pan=0,
    attack=0.01, decay=0.01, sustain=0.3, release=1.4, gate=1, cutoff=14000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(1, bufnum, rate: [lr, lr * 1.0], startPos: start * BufFrames.kr(bufnum), loop: 0) * env;
	// sig = FreqShift.ar(sig, 164, 0);
	sig = RLPF.ar(sig, cutoff, rq);
	sig = Compander.ar(sig, sig,
		thresh: -15.dbamp,
		slopeBelow: 1,
		slopeAbove: 0.5,
		clampTime:  0.01,
		relaxTime:  0.01
	);
	Out.ar(out, sig * amp);
}).add;

//------------------------------------------------------------
// visual : the drum grid itself. one mark per hit — role picks the lane,
// slot picks the position along the bar, so the canvas IS the 16-slot
// grid the DSL strings describe, and a multi-hit slot draws a vertical
// stack. Silent slots draw nothing, so the groove reads as its own
// notation. Atlas grammar G8 (event-triggered) over a G3 grid field.
//
//   role       -> vertical lane            (\sy -> \ey)
//   slot       -> horizontal position      (\sx -> \ex)
//   grid value -> mark size (ghost vs full vs accent)
//   layer band -> hue
~init = ~init <> {
	var folder = PathName("~/Music/cotf_samples/orchkit");

	// Pre-parse all grid strings once at load — runtime just reads arrays.
	parsedPatterns = rawPatterns.collect({|pat|
		pat.collect({|str| parseGrid.(str) })
	});

	group = Group.new;
	postf("loading samples : % \n", folder);
	bufs = folder.entries.collect({|path, i|
		var buf = Buffer.read(s, path.fullPath, action:{|b|
			postf("buffer alloc [% : %] \n", i, path.fileNameWithoutExtension);
		});
		buf
	});

	// Custom event: at each slot, iterate roles and spawn one synth
	// per active-hit role. baseAmp gate skips iteration when silent.
	// Accel-gated ghost dropout: at rest the threshold is higher so
	// only full hits play; motion drops the threshold to 0 so ghosts
	// come through.
	Event.addEventType(eventTypeName, {|e|
		// NB: inside Event.play the currentEnvironment is the event
		// itself — env-var lookups (`~buffers`) would resolve to
		// event[\buffers] (nil). File-scope `bufs` is a lexical
		// closure so it resolves regardless of currentEnvironment.
		var slot   = ~slotIdx;
		var pat    = parsedPatterns[currentPattern];
		var layer  = layers[currentLayer];
		var base   = ~baseAmp ? 0;
		var view   = ~viewID;
		var motion = m.accelMassFiltered.linlin(0, 2.0, 0, 1);
		var thresh = 0.4 * (1 - motion);   // 0.4 at rest → 0 at motion
		var hue    = switch(currentLayer,
			\low,  { Color.new(1.0, 0.4, 0.2) },
			\mid,  { Color.new(1.0, 0.75, 0.3) },
			\high, { Color.new(0.6, 0.9, 1.0) }
		);

		if (base > 0.001 and: { pat.notNil } and: { layer.notNil } and: { bufs.notNil }, {
			roles.do({|role, ri|
				var grid   = pat[role];
				var bufIdx = layer[role];
				if (grid.notNil and: { bufIdx.notNil }, {
					var v = grid.wrapAt(slot);
					if (v >= thresh and: { v > 0 }, {
						var lane = ri.linlin(0, roles.size - 1, -0.6, 0.6);
						var col  = slot.linlin(0, barLen - 1, -0.85, 0.85);
						(
							instrument: \drumkitOrch,
							bufnum:     bufs[bufIdx],
							group:      group,
							amp:        v * base,
							pan:        -0.08.rrand(0.08),
							attack:     0.02,
							decay:      1,
							release:    1.0,

							type:       \customVisualEvent,
							viewID:     view,
							shape:      \circle,
							sx:         col,
							ex:         col,
							sy:         lane,
							ey:         lane,
							startSize:  v.linlin(0, 1.2, 20, 110),
							endSize:    6,
							startColor: hue,
							endColor:   hue.copy.alpha_(0.0),
							startWidth: 3,
							endWidth:   0.4,
							duration:   0.7,
						).play;
					});
				});
			});
		});
	});

	// Pdef fires one event per 16th (\dur = 1 clock beat).
	// The event's slotIdx is derived from the clock so the grid stays
	// anchored to the bar.
	// NB: \baseAmp is deliberately NOT a Pbind key. Pbind keys win
	// over Pdef.set, so putting \baseAmp in here would silence every
	// event forever. Instead, .set flows via envir → each event's
	// ~baseAmp lookup returns the current tick-set value. Same for
	// \viewID.
	Pdef(m.ptn,
		Pbind(
			\type,    eventTypeName,
			\dur,     1,
			\slotIdx, Pfunc {|e|
				var pos = (TempoClock.beats - phase).mod(barLen);
				pos.round.asInteger.mod(barLen)
			},
		)
	);

	Pdef(m.ptn).set(\baseAmp, 0);   // safe starting value via envir
	Pdef(m.ptn).play(quant: [barLen, phase]);
};

//------------------------------------------------------------
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

//------------------------------------------------------------
// Sets baseAmp and the tilt-driven layer band, and runs the bar counter
// that swaps groove ↔ fill.
//
// NB: hard dead-zone on the sensor input. Below `deadZone` the tick
// FORCES baseAmp = 0 so the event handler's `base > 0.001` gate
// suppresses every hit — total silence at rest. Without it, filter
// decay residuals + sensor noise leak through `.lincurve(0, …)` and
// produce audible hits with no movement.
~next = { |d|
	var y = m.gyroYFiltered.clip(-1, 1);
	var raw = m.accelMassFiltered;
	var deadZone = 0.15;
	var amp = if (raw > deadZone,
		{ raw.lincurve(deadZone, 2.0, 0.0, 1.0, 1) },
		{ 0 }
	);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\baseAmp, amp);
	currentLayer = if (y < -0.33) {
		\low
	} {
		if (y < 0.33) { \mid } { \high }
	};

	// Every bar: increment counter. When we hit the fill-slot AND a fill
	// is defined, swap in the fill for one bar, otherwise stay on the
	// groove.
	if (TempoClock.beats > (barTime + barLen), {
		barTime = TempoClock.beats;
		barCounter = barCounter + 1;
		currentPattern = if (currentFill.notNil and: { barCounter.mod(fillEvery) == 0 }) {
			currentFill
		} {
			currentGroove
		};
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p| [m.gyroYFiltered, m.accelMassFiltered] };
