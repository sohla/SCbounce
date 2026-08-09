// templateVisual.sc
//
// A minimal personality with visuals, written to be copied. Every section is
// annotated with the rule it demonstrates - see CLAUDE.md at the repo root
// for the full contract.
//
// BEFORE designing the mark, read visuals/graphic-scores-atlas.md and take a
// grammar from it - §1 is the reusable core, §4 indexes the catalogue by
// grammar. Then name the lineage in this header, as magicWand.sc and bongo1.sc
// do. The shape below is a placeholder, not a design.
//
// NOT in any lists/ file on purpose : those drive the live rotation and a
// template should never turn up on a device mid-performance.

var m = ~model;

// Structure, not tunables. These mirror the SynthDef below so the drawing is
// provably the sound - change one and change the other. File-level vars are
// for structure like this, or for state; never for values you want to tweak.
// (CLAUDE.md C - "represent the synth", the tightness rule)
var partials    = [1, 2.76, 5.4];
var partialAmps = [1, 0.5, 0.25];

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.5;

//------------------------------------------------------------
SynthDef(\templateVisual, {
	|out = 0, freq = 300, amp = 0.2, decay = 1.2, pan = 0|
	var exciter = Impulse.ar(0) * PinkNoise.ar(1);
	var sig = DynKlank.ar(`[
		[1, 2.76, 5.4] * freq,		// <- partials
		[1, 0.5, 0.25],				// <- partialAmps
		[1, 0.7, 0.4] * decay
	], exciter);
	sig = sig * amp;
	DetectSilence.ar(sig, doneAction: Done.freeSelf);
	Out.ar(out, Pan2.ar(sig, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> {

	//--------------------------------------------------------
	// Before writing a shape at all, check code3.0/vdefLib.scd - the shared
	// library already has circle arc square triangle hexagon star cross
	// line wave spiral leaf blobby, and shape: \name uses them with no code
	// here whatsoever. Registering a name the library already defines
	// shadows it for this device only, which is how you start from a stock
	// shape and diverge.
	//
	// A POINTS FUNC - the preferred contract, and what every library entry
	// is. Returning an Array of Points hands \modulation, \closed and \fill
	// back to the core, so this only has to describe geometry.
	//
	// For a mark that is SEVERAL sub-paths - concentric rings, disjoint
	// strokes, separating edges - write a draw func instead: return anything
	// that is not an Array, and build it from
	//
	//   c[\render].(points, widthScale, alphaScale, closed)
	//   c[\draw].(\circle, (pos: p, size: r), widthScale, alphaScale, closed)
	//
	// closed defaults to the event's \closed, which is true - an open
	// sub-path such as an arc must pass false or it draws a chord.
	//
	// \modulation is windowed to zero at both ends of a path, so a 2-point
	// sub-path never warps however large amp is. Build spans as N-point
	// polylines if you want them to respond.
	//
	// Both route through the core's pipeline, so sub-paths still get
	// \modulation, \closed and \fill. Do NOT call Pen directly - see
	// bongo1.sc for a worked draw func that never touches it.
	//
	// Note what is NOT here: no position, no rotation, no Pen.width, no
	// colour. The core applied all of those before this ran.
	~vdef.(\templateMark, { |ev, c|
		var n = ev[\numPoints] ? 48;
		var mod = ev[\modulation] ? ();
		// Every constant is a fallback on an event lookup, so the shape is
		// tuned in the Pbind below and never in here.
		var lobes = mod[\lobes] ? 3;
		var depth = mod[\depth] ? 0.25;
		// Bracket access, ALWAYS. c.size would hit Set's `var <size` and
		// return the context Event's item count instead of the radius.
		var sz = c[\size];
		var pos = c[\pos];
		// normTime runs 0->1 over the event's \duration
		var t = c[\normTime];

		Array.fill(n, { |i|
			var a = i / n * 2pi;
			var r = sz * (1 + (depth * sin((a * lobes) + (t * 2pi))));
			pos + Polar(r, a).asPoint
		})
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \templateVisual,
			\note, Pseq([0, 4, 7, 11], inf),
			\octave, 5,
			\decay, 1.2,
			\pan, Pwhite(-0.4, 0.4),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],

			//------------------------------------------------
			// the visual block
			\type, \customVisualEvent,
			\shape, \templateMark,
			\numPoints, 48,
			\fill, false,

			// position : normalised, 0 = centre, +/-1 = edge.
			// Height follows pitch so notes separate vertically.
			\sy, Pfunc({ |e|
				((e[\note] ? 0) + ((e[\octave] ? 5) * 12)).linlin(48, 84, 0.7, -0.7)
			}),
			\ey, Pkey(\sy),
			\sx, Pwhite(-0.5, 0.5),
			\ex, Pkey(\sx),

			// Pfunc-with-default, not bare Pkey : ~next has not run when the
			// first events fire, so anything it supplies is still nil and
			// nil.linlin would throw.
			\startSize, Pfunc({ |e| (e[\amp] ? 0.2).linlin(0.02, 0.5, 20, 90) }),
			\endSize, Pkey(\startSize),

			// stroke weight = dynamic
			\startWidth, 4,
			\endWidth, 0.5,

			// pick a palette from a substrate (atlas 0.5). Grounds are fixed
			// black, so phosphor and saturated primaries work; ink-on-vellum
			// does not. Fade alpha to 0 so marks clear themselves.
			\startColor, Color.new(0.486, 1.0, 0.627, 0.9),
			\endColor, Color.new(0.486, 1.0, 0.627, 0.0),

			\rotation, Pwhite(0, 2pi),
			\duration, 1.4,

			// \modulation is the ONLY free-form passthrough - any other
			// custom key is silently dropped by the event constructor. So
			// every non-standard input the draw func needs travels here.
			//
			// e.isRest matters if the \dur pattern contains Rests : rest
			// events still fire the visual type, and unguarded they draw
			// marks into the silences.
			\modulation, Pfunc({ |e| (
				rest: e.isRest,
				lobes: 3,
				depth: 0.25
			) })
		);
	);

	Pdef(m.ptn).set(\dur, 0.4);
	Pdef(m.ptn).play(quant: 0.1);
	Pdef(m.ptn).pause;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
};

//------------------------------------------------------------
~next = {|d|

	var amp = m.accelMassFiltered.lincurve(0, 2.0, 0.02, 0.5, 2);

	// REQUIRED. Without it the event is dropped by the router and you get
	// audio with no picture - and no error to tell you why.
	Pdef(m.ptn).set(\viewID, d.port);

	Pdef(m.ptn).set(\amp, amp);

	if(m.accelMassFiltered > 0.05, {
		if( Pdef(m.ptn).isPlaying.not, {
			Pdef(m.ptn).resume(quant: 0.4);
		});
	},{
		if( Pdef(m.ptn).isPlaying, {
			Pdef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0, 2, 0, 1)];
};
