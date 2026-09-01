/*
gestures:    [beat, shake]
description: TODO — what it IS, mechanically. State the rule the file runs on, not what it sounds like. This is the long one.
sound:       TODO — what you hear
pitch:       TODO — where notes come from
rhythm:      TODO — rhythmic behaviour
instruments: [Template]
*/

// _TEMPLATE_ak_pfile.sc
//
// Skeleton for a new AirKit personality. Copy to personalities/<name>.sc,
// fill the TODOs, delete what you don't need, delete this header.
//
//   code3.0/ak_pfile_authoring.md   the contract, the model, the traps
//   CLAUDE.md                       the visual event API (authoritative)
//   visuals/graphic-scores-atlas.md read BEFORE designing a mark
//
// Engine here is B : Pdef + per-event synth voice, the most common shape.
//   engine A (one long-lived Synth)  -> metal1.sc, wind1.sc, drone1.sc
//   engine C (Pdef + sampler)        -> cotf_marimba1.sc, multiBeat5.sc
//   one-shots (no Pdef at all)       -> see the throttle block in ~next
//
// NOT in any lists/ file on purpose : those drive the live rotation and a
// template must never turn up on a device mid-performance.

var m = ~model;
var group;             // dedicated Group — everything this file makes lives here
var lastTime = 0;      // throttle anchor for one-shots. An accumulator across
                       // frames, which is one of the two things a file-level
                       // var is for. The other is structure, below.

// STRUCTURE, not tunables. Mirror the SynthDef's own constants here and drive
// the drawing from them, so the visual is provably the sound. Anything you
// would want to tweak belongs on the event instead.
// TODO(structure): replace or delete.
var partials    = [1, 2.76, 5.4];
var partialAmps = [1, 0.5, 0.25];

//------------------------------------------------------------
// Filter tuning. attack = coefficient while the signal RISES, decay while it
// falls; higher is slower. A fast attack + slow decay turns a hit into an
// envelope you can map. TODO(filter-tuning): tune to the gesture you want.
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// TODO(synthdef): the voice. Name it after the file so two personalities
// never collide on a SynthDef name.
SynthDef(\templateAk, { |out = 0, freq = 300, amp = 0.2, decay = 1.2, pan = 0|
	var exciter = Impulse.ar(0) * PinkNoise.ar(1);
	var sig = DynKlank.ar(`[
		[1, 2.76, 5.4] * freq,      // <- partials
		[1, 0.5, 0.25],             // <- partialAmps
		[1, 0.7, 0.4] * decay
	], exciter);
	sig = sig * amp;
	DetectSilence.ar(sig, doneAction: Done.freeSelf);
	Out.ar(out, Pan2.ar(sig, pan));
}).add;

//------------------------------------------------------------
// visual : TODO — what the mark IS, the atlas lineage it comes from, and
// which musical parameter drives which visual dimension. That mapping table
// is the useful part and the part nothing else records.
//
// Lineage: TODO — atlas grammar G?, traversal ?. Never approximate a
// published score page; build an original field from the same primitives.
//
//   TODO -> horizontal position   (\sx -> \ex)
//   TODO -> vertical position     (\sy -> \ey)
//   amp  -> mark size
//   dur  -> how long the mark lives
//
// ~init runs ONCE, on a Routine, between two s.sync. It is handed d.
~init = ~init <> { |d|

	// Helpers used only here belong here, not at file level. Keep code
	// close to its use.
	// var myHelper = { |x| ... };

	group = Group.new;

	//--------------------------------------------------------
	// Before writing a shape at all, check code3.0/vdefLib.scd — circle arc
	// square triangle hexagon star cross line wave spiral leaf blobby are
	// already there and `shape: \name` uses them with no code here at all.
	// Registering a name the library defines SHADOWS it, for this device
	// only — the intended way to start from a stock shape and diverge.
	//
	// Two contracts, chosen by what you return:
	//   POINTS FUNC — return an Array of Points. \modulation, \closed and
	//                 \fill are then applied for you. Preferred.
	//   DRAW FUNC   — several sub-paths. Return anything not an Array, and
	//                 END IT WITH nil. Build it from c[\render] / c[\draw],
	//                 never from Pen. See bongo1.sc.
	//
	// The core has already applied position, rotation, Pen.width and both
	// pen colours. Read the context with BRACKETS — c[\size], never c.size.
	// TODO(vdef): design the mark, or delete this and use a library shape.
	~vdef.(\templateMark, { |ev, c|
		var n = ev[\numPoints] ? 48;
		var mod = ev[\modulation] ? ();
		var lobes = mod[\lobes] ? 3;
		var depth = mod[\depth] ? 0.25;
		var sz = c[\size];
		var pos = c[\pos];
		var t = c[\normTime];

		Array.fill(n, { |i|
			var a = i / n * 2pi;
			var r = sz * (1 + (depth * sin((a * lobes) + (t * 2pi))));
			pos + Polar(r, a).asPoint
		})
	});

	// TODO(pdef): the pattern. Any key ~next owns must NOT appear here —
	// a Pbind key overrides the envir and defeats Pdef.set.
	Pdef(m.ptn,
		Pbind(
			\instrument, \templateAk,
			\group, group,
			\note, Pseq([0, 4, 7, 11], inf),
			\octave, 5,
			\decay, 1.2,
			\pan, Pwhite(-0.4, 0.4),
			\func, Pfunc({ |e| ~onEvent.(e) }),
			\args, #[],

			//------------------------------------------------
			// the visual block
			\type, \customVisualEvent,
			\shape, \templateMark,
			\numPoints, 48,
			\fill, false,

			// normalised: 0 = canvas centre, ±1 = edge
			\sx, Pwhite(-0.5, 0.5),
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e|
				((e[\note] ? 0) + ((e[\octave] ? 5) * 12)).linlin(48, 84, 0.7, -0.7)
			}),
			\ey, Pkey(\sy),

			// Pfunc-with-default, not bare Pkey : ~next has not run when the
			// first events fire, so nil.linlin would throw.
			\startSize, Pfunc({ |e| (e[\amp] ? 0.2).linlin(0.02, 0.5, 20, 90) }),
			\endSize, Pkey(\startSize),

			\startWidth, 4,
			\endWidth, 0.5,

			// Derive the palette from a substrate (atlas §0.5). Grounds are
			// fixed black, so phosphor and saturated primaries work;
			// ink-on-vellum does not. Fade alpha to 0 so marks clear.
			\startColor, Color.new(0.486, 1.0, 0.627, 0.9),
			\endColor, Color.new(0.486, 1.0, 0.627, 0.0),

			\rotation, 0,
			\duration, 1.4,

			// \modulation is the ONLY free-form passthrough — any other
			// custom key is silently dropped by the event constructor.
			// e.isRest matters if \dur contains Rests: rest events still
			// fire the visual type and would draw into the silences.
			\modulation, Pfunc({ |e| (
				rest: e.isRest,
				lobes: 3,
				depth: 0.25,
			) }),
		);
	);

	// Seed the envir for EVERY key ~next supplies — ~next has not run yet.
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\dur, 0.4);

	Pdef(m.ptn).play(quant: 0.1);
};

//------------------------------------------------------------
// Fires before every reload — and saving the file IS a reload, so this is on
// the hot path of your own edit loop. Guards make it safe to run twice.
// TODO(deinit): add buffer / held-synth teardown if this file has either.
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	// Event.eventTypes.removeAt(eventTypeName);   // if you registered one

	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		// if (buffers.notNil) {
		//     buffers.do({ |b| postf("buffer dealloc [%] \n", b); b.free; s.sync });
		//     buffers = nil;
		// };
	};
};

//------------------------------------------------------------
// Convention, not a hook — nothing calls this but the \func above. It is how
// a personality PUBLISHES to m.com, the one channel shared across devices.
// Readers do `m.com.root ? 0`. Delete if this file publishes nothing.
~onEvent = { |e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};

//------------------------------------------------------------
// Runs ~100 Hz. Everything gesture-driven happens here, and NOTHING is
// allocated here — no Synth, no Buffer, no Pdef — except through a throttle.
~next = { |d|
	// TODO(mapping): resolve gesture -> parameter. lincurve clips at both
	// ends, so it is safe on a sensor that overshoots. Negative curve rises
	// fast then flattens (amplitude, density); positive starts slow
	// (filter cutoff).
	var amp = m.accelMassFiltered.lincurve(0, 1.4, -50, -5, -1);
	var tilt = (d.sensors.gyroEvent.y / pi.half);

	// REQUIRED if this file draws anything. Without it the visual event is
	// dropped by the router and you get audio with no picture — and no error
	// to say why.
	Pdef(m.ptn).set(\viewID, d.port);

	Pdef(m.ptn).set(\amp, amp.dbamp);

	// TODO(reader): if this file follows another's key, uncomment.
	// Pdef(m.ptn).set(\root, m.com.root ? 0);

	// TODO(one-shot): the no-pattern idiom. ~next runs 100×/sec, so anything
	// that fires a synth needs a gate or one shake makes a hundred voices.
	// if (TempoClock.beats > (lastTime + 0.25), {
	//     (instrument: \templateAk, freq: 440, amp: 0.2, group: group).play;
	//     lastTime = TempoClock.beats;
	// });
};

//------------------------------------------------------------
// Runs ~33 Hz into the device plotter. Effectively required: a nil ~plot
// throws `Message '-' not understood` 33×/sec, which is also the symptom of
// the whole file failing to compile. Put the RAW and the FILTERED value side
// by side — most mapping bugs are a range problem you can see in one glance.
// NB ~plotMin / ~plotMax are read once, when the view is built, not per save.
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p|
	[m.accelMass, m.accelMassFiltered];
};
