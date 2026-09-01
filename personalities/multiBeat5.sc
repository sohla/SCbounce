/*
gestures:    [beat, shake, tilt]
description: cotf_marimba1's sample player on the multiBeat grid. The dur mechanic is multiBeat1's, unchanged — every group fills exactly ONE beat, n notes of dur beat/n, built from Pswitch so the subdivision can only change at a group boundary — and the ladder runs [1 2 4 8] so a hard gesture turns the figure into a marimba roll. It does not choose its own key: multiBeat1 writes m.com.root on every note and this file reads it, the way multiBeat3 does, so the three sit in the same harmony without knowing about each other. What it adds to the family is the sampler. The African Marimba library only has D/F/A/B per octave, so almost every note asked for is played by the nearest sample resampled up or down by a semitone or two — the pitch you hear is never quite the pitch that was recorded, and that gap is the character of the instrument. Tilt picks the octave, roll bends the whole playback rate on top of the resample.
sound:       bright African marimba mallet strikes through cotf_marimba1's stereo sample voice; crisp transient, natural tail, release scaled to the subdivision so rolls stay tight
pitch:       a pool of degrees over m.com.root at an octave from tilt; nearest-sample lookup then resamples to hit it
rhythm:      n hits per beat, n from accel, ladder [1 2 4 8] on a 0.5 beat. step 0 of every group is the accent
instruments: [Gravitone]
*/

var m = ~model;
var group;
var samplesLib;

// THE RULE, multiBeat1's, verbatim.
//
//   Pn(n, n)             -> n copies of n      : the dur denominator, latched for the group
//   Pseries(0, 1, n)     -> 0 .. n-1           : where we are inside the group
//   Pseq(pool.keep(n),1) -> first n pool notes : the figure lengthens as it subdivides
//
// All three are FINITE patterns of length n, so all three Pswitches end
// their group on the same event and re-read \divIdx together. That
// lockstep is what keeps the count, the dur and the figure agreeing.
//
// pool is degrees over m.com.root, and it needs at least divs.last
// entries — at div 1 you hear only the root, at div 8 the whole arch.
var beat = 0.5;
var divs = [1, 2, 4, 8];
var pool = [0, 4, 7, 12, 14, 12, 7, 4] + 12;

// Unique per-env event type — one personality per device, so the name
// carries the pattern key to stay distinct.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// cotf_marimba1's sample voice, under its own name so the two files can
// be loaded on two devices at once. ptch multiplies the playback rate on
// top of the resample rate — continuous pitch bend over the lookup.
SynthDef(\multiBeatSampler, {|bufnum=0, out=0, amp=1, rate=1, ptch=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1, cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.007], startPos: start * BufFrames.kr(bufnum), loop: 0);
	Out.ar(out, sig * amp * env);
}).add;

//------------------------------------------------------------
// visual : the bar that was struck, and the bar that actually sounded.
// A marimba is a row of slabs, so each note is a slab — laid on the
// multiBeat grid, x is the step within the beat and y is the sounding
// pitch, the same field multiBeat3 and multiBeat4 draw into, so the
// devices read as one score. Bar length is the note's dur, so the
// subdivision is legible as the slabs shortening.
//
// Under each slab sits a second, dimmer one at the pitch of the sample
// the lookup actually reached for. When the library happens to hold the
// note the two coincide and you see one clean bar; when it does not, the
// bar doubles and the offset you see IS the semitones the sampler had to
// resample by. That is the instrument's own mechanism, not a decoration
// of it.
//
// Lineage: register-band and piano-roll notation laid over a step-grid
// field. Atlas grammars G4 (block / register-band) and G3 (grid), with
// traversal 6 (event-triggered). Palette from the colour-field
// improvisation row of §0.5 — flat saturated amber, no grey.
//
//   step in group  -> horizontal position   (\step -> \sx)
//   sounding pitch -> vertical position     (-> \sy)
//   dur            -> length of the slab    (-> \startSize)
//   amp            -> depth of the slab     (-> \startWidth)
//   octave         -> hue
//   resample shift -> offset of the ghost slab  (\modulation)
//   ptch bend      -> shear of the whole mark   (-> \rotation)
//   dur            -> how long the mark lives   (-> \duration)
~init = ~init <> {

	// note-name → MIDI, and the marimba's own filename shape:
	//   "Marimba ln <dyn> l<N>x  <octave><note>.aif"
	// space-delimited rather than underscore-delimited, and
	// octave-then-letter rather than letter-then-octave, hence the
	// reorder before noteToMidi. Both are cotf_marimba1's, unchanged.
	var noteToMidi = { |noteName|
		var pattern = "([A-G](#|b)?)([0-9])";
		var noteNames = "C C# D D# E F F# G G# A A# B";
		var parts, note, octave, noteIndex;
		parts = noteName.findRegexp(pattern);
		if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw});
		note = parts[1][1];
		octave = parts[3][1].asInteger;
		note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
		           .replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
		noteIndex = noteNames.split($ ).find([note]);
		(octave + 1) * 12 + noteIndex;
	};
	var parseMarimbaNote = { |fileStem|
		var parts = fileStem.findRegexp("([0-9])[ ]*([a-g])$");
		if(parts.size < 3, { Error("Invalid marimba note format: %".format(fileStem)).throw });
		noteToMidi.(parts[2][1].toUpper ++ parts[1][1]);
	};

	// One dynamic + one layer, giving 18 unique pitches F2..B6. "f l1x"
	// for a louder set, "l2x"/"l3x" for the other round-robin takes.
	var sampleFilter = "Marimba ln mf l1x";
	var folder = PathName("~/Downloads/cotf_samples/African Marimba");

	// THE LOOKUP : how this instrument answers a pitch. The library has
	// only D/F/A/B per octave — 4 of 12, mixed odd and even — so there is
	// no resample trick that lands on the note. Find the closest sample,
	// resample by the difference, and hand the difference back so the
	// visual can draw the gap it left.
	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio, shift: semitoneDiff)
	};

	~vdef.(\bar, { |ev, c|
		var mod = ev[\modulation] ? ();
		var lane = mod[\lane] ? 15;
		var shift = mod[\shift] ? 0;
		var ghost = mod[\ghost] ? 0.3;
		var mid = c[\pos];
		var halfLen = c[\size];
		var halfDep = c[\width];
		var slab = { |dy|
			[ (mid.x - halfLen) @ (mid.y + dy - halfDep),
			  (mid.x + halfLen) @ (mid.y + dy - halfDep),
			  (mid.x + halfLen) @ (mid.y + dy + halfDep),
			  (mid.x - halfLen) @ (mid.y + dy + halfDep) ]
		};
		c[\render].(slab.(shift * lane), 1, ghost, true);
		c[\render].(slab.(0), 1, 1, true);
		nil
	});

	// scan folder, filter to the chosen dynamic+layer, parse each name.
	samplesLib = folder.entries
		.select({|path| path.fileName.contains(sampleFilter) })
		.collect({ |path|
			var midiNote = parseMarimbaNote.(path.fileNameWithoutExtension);
			var buffer = Buffer.read(s, path.fullPath, action:{|buf|
				postf("buffer alloc [%] \n", buf);
			});
			postf("loading sample : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
			(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
		});

	if(samplesLib.size == 0, {
		"[multiBeat5] no samples matched '%' in % - the pattern will stay silent"
			.format(sampleFilter, folder.fullPath).warn;
	});

	// custom event handler — resolve the sounding MIDI note, pick the
	// buffer and rate, hand the resample offset to the visual through
	// \modulation (the only free-form channel the event carries), then
	// delegate to \customVisualEvent, which draws and re-types to \note.
	Event.addEventType(eventTypeName, {|e|
		var found;
		if(samplesLib.size > 0, {
			~note = ~note + ~root + (12 * ~octave);
			found = findClosestSample.(~note);
			~bufnum = found.buffer;
			~rate = found.rate;
			~modulation = ~modulation ? ();
			~modulation[\shift] = found.shift;
			~type = \customVisualEvent;
			currentEnvironment.play;
		});
	});

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatSampler,
			\group, group,
			\type, eventTypeName,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx)),
			\dur,  Pkey(\div).reciprocal * beat,
			\octave, Pseq([3,4,5].stutter(4), inf),
			\legato, 0.8,
			\attack, 0.004,
			\release, (Pkey(\dur) * 4) + 0.2,
			\amp, Pkey(\energy) * Pfunc({ |e| if(e[\step] == 0, { 1.0 }, { 0.6 }) }),
			\pan, Pfunc({ |e| ((e[\panBias] ? 0) + rrand(-0.2, 0.2)).clip(-1, 1) }),
			\start, 0,

			\shape, \bar,
			\fill, true,
			\sx, (Pkey(\step) / Pkey(\div) * 1.5) - 0.75,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e|
				(e[\note] + (e[\root] ? 0) + (12 * (e[\octave] ? 5)))
					.linlin(45, 92, 0.7, -0.7)
			}),
			\ey, Pkey(\sy),
			\rotation, Pfunc({ |e| (e[\ptch] ? 1).log2 * 2 }),
			\startSize, Pkey(\dur) * 380,
			\endSize, Pkey(\dur) * 380,
			\startWidth, (Pkey(\amp) * 22) + 3,
			\endWidth, 1,
			\startColor, Pfunc({ |e|
				Color.hsv(((e[\octave] ? 5) - 4).linlin(0, 2, 0.055, 0.15), 0.85, 1.0, 0.9)
			}),
			\endColor, Pfunc({ |e|
				Color.hsv(((e[\octave] ? 5) - 4).linlin(0, 2, 0.055, 0.15), 1.0, 0.35, 0.0)
			}),
			\duration, (Pkey(\dur) * 4) + 0.2,
			\modulation, Pfunc({ |e| (lane: 15, ghost: 0.3, amp: 0) }),
		);
	);

	// Seed the envir — ~next has not run when the first events fire, and
	// a nil \divIdx would index the Pswitch lists with nil.
	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\energy, 0);
	Pdef(m.ptn).set(\root, 0);
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\ptch, 1);
	Pdef(m.ptn).set(\panBias, 0);

	Pdef(m.ptn).play(quant: 1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

	// Kill synths first (latency-safe /g_freeAll), then free sample
	// buffers — order matters so no PlayBuf is still reading from a
	// buffer we're about to /b_free. fork so s.sync actually waits.
	// Idempotent: notNil guards let ~deinit fire twice safely.
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (samplesLib.notNil) {
			samplesLib.do({|sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
// Accel climbs the subdivision ladder and sets the level; tilt picks the
// octave; roll bends the playback rate on top of the resample and twist
// leans the stereo image. \divIdx, \energy, \root, \octave, \ptch and
// \panBias are deliberately NOT Pbind keys, because a Pbind key overrides
// the envir and defeats these .sets.
~next = {|d|
	var e = m.accelMassFiltered;
	var idx = e.lincurve(0, 1.5, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = e.lincurve(0, 1.4, -41, 0 -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 6, 1).asInteger;
	var ptch = (d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, 0.94, 1.06);
	var panBias = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).linlin(-0.5, 0.5, -0.5, 0.5);

	if(amp < 40.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\energy, amp.dbamp);
	Pdef(m.ptn).set(\root, m.com.root ? 0);
	Pdef(m.ptn).set(\octave, oct);
	// Pdef(m.ptn).set(\ptch, ptch);
	Pdef(m.ptn).set(\panBias, panBias);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass, m.accelMassFiltered, (d.sensors.gyroEvent.y / pi.half)];
};
