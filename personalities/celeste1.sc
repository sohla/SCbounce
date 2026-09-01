/*
gestures:    [beat, shake]
description: A cyclic score for the celeste. Every note takes the next position on a 96 step ring, and the dur pattern under it is 11 steps long, so the figure never lands on the same points twice — it precesses, a lap at a time. The rests in that dur pattern are real: they take a ring position and draw nothing, so the gaps are as structural as the strikes. Movement is the on switch and nothing else: below the threshold the pattern is paused mid-ring and the canvas empties, above it the ring fills again from wherever it stopped.
sound:       celeste samples, one voice per strike, tail scaling with how hard you are moving — still and it rings out, hard and it goes dry and close
pitch:       a pentatonic pool over three octaves of the sample library, nearest-sample lookup with a ≤1 semitone shift
rhythm:      a fixed 11 step dur pattern with three rests in it, laid round a 96 step ring
instruments: [Lumivox]
*/

var m = ~model;
var group;
var samplesLib;

// Unique per-env event type — a shared \customEvent registration would be
// clobbered by the next device to load a sampler.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

var folder = PathName("~/Downloads/cotf_samples/Celesta_ES_mf");

// Position around the ring. State, not a tunable : it counts events, and
// nothing in m can recompute where the last lap got to. The ring LENGTH
// is on the event, next to everything else.
var step = 0;

//------------------------------------------------------------
// note-name -> MIDI, for the library's "CE_ES_mf_<NOTE>.wav" stems. The
// note token is the last underscore-separated piece of the stem.
var noteToMidi = { |noteName|
	var pattern = "([A-G](#|b)?)([0-9])";
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts, note, octave, noteIndex;
	parts = noteName.findRegexp(pattern);
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw });
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
		.replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
	(octave + 1) * 12 + noteIndex;
};

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.99;

//------------------------------------------------------------
SynthDef(\celesteVoice, {|out=0, bufnum=0, amp=0.2, rate=1, start=0, pan=0,
    attack=0.005, sustain=0.4, release=1.6|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum),
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp * env));
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// nearest-sample lookup. The library is every semitone at the bottom
	// and every whole tone above that, so the shift is never more than a
	// semitone and the bell keeps its character.
	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		(buffer: closest.buffer, rate: (targetMidi - closest.midiNote).midiratio)
	};

	//--------------------------------------------------------
	// visual : a cyclic score. Each strike takes the next position round
	// a 96 step ring and its radius is its pitch, so a rising phrase
	// spirals outward as it goes round. The dur pattern under it is 11
	// steps long, so the figure walks a few positions each lap instead of
	// stamping the same points — and its three rests take a position and
	// draw nothing, which is what makes the gaps legible as gaps.
	//
	// The mark is a gong point : a struck head with a spoke trailing out
	// along the radius, both shrinking on the sample's own tail, so what
	// you see decaying is what you hear decaying.
	//
	// Lineage: gong-point and nested-cycle notations from the atlas, in
	// the Ikeda-ish white-on-black data palette rather than a warm one —
	// the celeste is a cold instrument. Atlas grammar G8, palette §0.5.
	//
	//   ring position -> angle          (\cyc -> \rotation)
	//   pitch         -> radius         (\rad -> \startSize)
	//   amp           -> head size      (\modulation)
	//   release       -> how long the mark lives, and how fast it shrinks
	//
	// A draw func : head and spoke are two sub-paths. It knows no geometry
	// of its own — the event sits at the canvas centre, \rotation carries
	// the angle and \startSize is the radius, so it only steps out along
	// +x and asks c[\draw] for library shapes.
	~vdef.(\gongPoint, { |ev, c|
		var mod = ev[\modulation] ? ();
		var rest = mod[\rest] ? false;
		var head = mod[\head] ? 26;
		var spoke = mod[\spoke] ? 90;
		var decay = mod[\decay] ? 0.35;
		var t = c[\normTime];
		var mid = c[\pos];
		var ring = c[\size];
		var pos = mid + (ring @ 0);
		var a = exp(t.neg / decay);
		var reach = spoke * a;

		if(rest.not, {
			c[\draw].(\circle, (pos: pos, size: head * (1 + (2 * (1 - a)))), a, a);
			c[\draw].(\line, (pos: pos + ((reach * 0.5) @ 0), size: reach * 0.5), a, a, false);
		});
		nil
	});

	// Load the library. Every stem ends in its note name, so the MIDI note
	// each buffer holds is known before a single note is played.
	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action: { |buf|
			postf("buffer alloc [%] \n", buf);
		});
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});
	postf("loading celeste : % samples \n", samplesLib.size);

	// The event type resolves pitch to a buffer and a rate, then hands on
	// to \customVisualEvent, which draws the mark and re-types to \note.
	Event.addEventType(eventTypeName, { |e|
		var target = ~note + ~root + (12 * ~octave);
		var found = findClosestSample.(target);
		~bufnum = found.buffer;
		~rate = found.rate;
		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \celesteVoice,
			\group, group,
			\type, eventTypeName,

			\dur, Pseq([0.5, Rest(0.5), 0.25, 0.25, 0.5, Rest(0.25), 0.75, 0.25, Rest(0.5), 0.5, 0.25] * 0.5, inf),
			\note, Pseq([0, 2, 4, 7, 9], inf),
			\octave, Pseq([6, 6, 7, 6, 5, 6, 7], inf),
			\root, 0,
			\legato, 0.4,
			\pan, Pwhite(-0.35, 0.35),

			\shape, \gongPoint,
			\cyc, Pfunc({
				var ringSteps = 96;
				var ph = (step % ringSteps) / ringSteps;
				step = step + 1;
				ph
			}),
			\rad, Pfunc({ |e|
				((e[\note] ? 0) + ((e[\octave] ? 6) * 12)).linlin(60, 96, 0.3, 0.8)
			}),
			\rotation, Pfunc({ |e| (e[\cyc] * 2pi) - 0.5pi }),
			\startSize, Pfunc({ |e| e[\rad] * 380 }),
			\endSize, Pkey(\startSize),
			\startColor, Color.new(1.0, 1.0, 1.0, 0.9),
			\endColor, Color.new(0.35, 0.85, 1.0, 0.0),
			\startWidth, 4,
			\endWidth, 0.5,
			\duration, Pfunc({ |e| (e[\release] ? 1.6) * 1.4 }),
			\modulation, Pfunc({ |e| (
				rest: e.isRest,
				head: (e[\amp] ? 0.2).clip(0.01, 1).linexp(0.01, 1, 8, 40),
				spoke: 110,
				decay: ((e[\release] ? 1.6) * 0.22).clip(0.08, 0.6),
				amp: 0
			) }),

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: 1);
	Pdef(m.ptn).pause;

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\release, 1.6);
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
		if (samplesLib.notNil) {
			samplesLib.do({ |sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
// Movement is the on switch. Below the threshold the pattern pauses where
// it is — the ring keeps its position, so picking the stick back up
// carries on round rather than restarting the lap.
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0, 1.4, -60, -14, -1);
	var release = m.accelMassFiltered.lincurve(0, 1.4, 2.6, 0.5, 2);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\release, release);

	if(m.accelMassFiltered > 0.03, {
		if(Pdef(m.ptn).isPlaying.not, {
			Pdef(m.ptn).resume(quant: 0.125);
		});
	}, {
		if(Pdef(m.ptn).isPlaying, {
			Pdef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass, m.accelMassFiltered];
};
