/*
gestures:    [beat, shake]
description: The hammered dulcimer's repeated-note tremolo, built as a subdivision ladder. Every group fills exactly ONE beat, so the note count and the dur are two views of the same number: n notes of dur 1/n. Accel picks n off the ladder [1 2 3 4], and because the whole figure is built from Pswitch the subdivision can only change at a group boundary — the tremolo thickens and thins between beats but never drifts inside one. The note material lengthens with it: n notes of the pool, so a single sustained strike at rest becomes a four note run flat out. It publishes root and dur on m.com, so another p-file can follow the key it is walking through.
sound:       hammered dulcimer samples, one voice per strike, tail scaling with dur so fast tremolo stays tight and slow strikes ring
pitch:       first n notes of a local pool over a root that moves every 32 notes; nearest-sample lookup, ≤2 semitone shift
rhythm:      n strikes per beat, n from accel, on a half second beat
instruments: [Cellaris]
*/

var m = ~model;
var group;
var samplesLib;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

var folder = PathName("~/Downloads/cotf_samples/Celtic Hammered Dulcimer");
// Articulation, embedded in the stem : hrd = hard mallets, the open tone.
// med / sft / mtd are softer, trill is pre-trilled.
var sampleFilter = "Dlcmr-hrd";

// THE RULE, in one line each.
//
//   Pn(n, n)             -> n copies of n      : the dur denominator, latched for the group
//   Pseries(0, 1, n)     -> 0 .. n-1           : where we are inside the group
//   Pseq(pool.keep(n),1) -> first n pool notes : the figure lengthens as it subdivides
//
// All three are FINITE patterns of length n, so all three Pswitches end
// their group on the same event and re-read \divIdx together. That
// lockstep is what keeps the count and the dur from disagreeing.
var beat = 0.5;
var divs = [2, 4, 6];
var pool = [0, 11, 7, 4, 2, -1];   // needs at least divs.last entries

//------------------------------------------------------------
// note-name -> MIDI, and the library's own stem shape:
//   "IL Hmr Dlcmr-hrd F#2c" -> note F#2, take c. The trailing variant
// letter is stripped, so every take of a pitch resolves to one note.
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

var parseDulcimerNote = { |fileStem|
	var parts = fileStem.findRegexp("([A-G](#|b)?[0-9])[a-z]?$");
	if(parts.size < 2, { Error("Invalid dulcimer note format: %".format(fileStem)).throw });
	noteToMidi.(parts[1][1]);
};

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;

//------------------------------------------------------------
SynthDef(\dulcimerVoice, {|out=0, bufnum=0, amp=0.2, rate=1, start=0, pan=0,
    attack=0.002, sustain=0.2, release=1.2|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum),
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp * env));
}).add;

//------------------------------------------------------------
// visual : the subdivision as a piano roll. Each group lays its n strikes
// left to right across one beat, so the tremolo density you hear is the
// density you see, and a triplet reads as three ticks where a four run
// reads as four. Height is the sounding pitch, so the pool climbs the
// canvas and the whole stack steps whenever the root moves.
//
// Lineage: pin-barrel and piano-roll notation — the marks are the pins,
// the beat is one turn of the barrel. Atlas grammars G3 (grid) and G10
// (machine-legible), on the phosphor palette from §0.5.
//
//   step in group -> horizontal position   (\sx -> \ex)
//   note + root   -> height                (-> \sy)
//   dur           -> length of the tick    (-> \startSize)
//   amp           -> stroke weight
~init = ~init <> {

	// Nearest-sample lookup. Coverage is a G major scale across four
	// octaves, so a chromatic target is never more than two semitones
	// from a real strike.
	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		(buffer: closest.buffer, rate: (targetMidi - closest.midiNote).midiratio)
	};

	samplesLib = folder.entries
		.select({ |path| path.fileName.contains(sampleFilter) })
		.collect({ |path|
			var midiNote = parseDulcimerNote.(path.fileNameWithoutExtension);
			var buffer = Buffer.read(s, path.fullPath, action: { |buf|
				postf("buffer alloc [%] \n", buf);
			});
			(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
		});
	postf("loading dulcimer : % samples \n", samplesLib.size);

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
			\instrument, \dulcimerVoice,
			\group, group,
			\type, eventTypeName,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx)),
			\root, Pseq([0, 2,-2,0].stutter(24), inf),
			\octave, 4,
			\dur,  Pkey(\div).reciprocal * beat,
			\legato, 0.8,
			\release, Pkey(\dur) * 2.2,
			\pan, Pwhite(-0.3, 0.3),

			\shape, \line,
			\rotation, pi.half,
			\sx, (Pkey(\step) / Pkey(\div) * 1.5) - 0.75,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| ((e[\note] ? 0) + (e[\root] ? 0)).linlin(-4, 14, 0.6, -0.6) }),
			\ey, Pkey(\sy),
			\startSize, Pkey(\dur) * 90,
			\endSize, Pkey(\dur) * 90,
			\startColor, Color.new(0.49, 1.0, 0.63, 0.9),
			\endColor, Color.new(0.1, 0.4, 0.2, 0.0),
			\startWidth, Pfunc({ |e| ((e[\amp] ? 0.2) * 22) + 1 }),
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 3,
			\func, Pfunc({ |e| ~onEvent.(e) }),

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: 1);

	// Seed the envir — ~next has not run when the first events fire, and a
	// nil \divIdx would index the Pswitch lists with nil.
	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
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
// Publish the key and the beat this file is walking through, so another
// p-file can follow it.
~onEvent = {|e|

	m.com.root = e.root;
	m.com.dur = e.dur;
};

//------------------------------------------------------------
// Accel drives the ladder index, and nothing else touches it — \divIdx is
// deliberately NOT a Pbind key, because a Pbind key would override the
// envir and defeat this .set.
~next = {|d|
	var idx = m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 1.2, -60, -12, -1);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass, m.accelMassFiltered];
};
