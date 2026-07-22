/*
Grain topology 2 of 3: Warp1 pointer-scan stretcher
---------------------------------------------------
Per-event synth. Each pattern event fires a fresh Warp1 that reads a
stereo harp buffer via a 0..1 pointer, using `overlaps` window readers
for smooth time-stretched output. Handles multichannel buffers natively,
so samples load as STEREO via Buffer.read.

Character: freeze / stretch / scrub. Set posLfoRate high for scrubby
motion, keep low with small posLfoDepth for a slowly-drifting freeze.

Sample coverage: same mapping as file 1 (nearest-lower sampled pc + rate
bend). ~harpPCSampled / ~harpPCOffset defined here if the tgrains file
hasn't been evaluated first.

Pattern keys for pitch: \note (raw MIDI, e.g. 60 = middle C), plus
\root (additive semitone shift, default 0) and \octave (SC convention,
default 5 = no shift; 6 = +1 octave, 4 = -1 octave). \trans is a
post-selection playback-rate multiplier — separate lever, unchanged.
*/

// ---------------------------------------------------------------- LOADER
(
if(~harpStereo.isNil, {
	var noteToMidi = { |noteName|
		var pattern = "([A-G](#|b)?)([0-9])";
		var noteNames = "C C# D D# E F F# G G# A A# B";
		var parts = noteName.findRegexp(pattern);
		var note = parts[1][1];
		var octave = parts[3][1].asInteger;
		var noteIndex;
		note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
		           .replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
		noteIndex = noteNames.split($ ).find([note]);
		(octave + 1) * 12 + noteIndex;
	};
	~harpStereo = PathName("~/Music/cotf_samples/harp").entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buf  = Buffer.read(s, path.fullPath);
		(name: path.fileNameWithoutExtension, buffer: buf, midiNote: noteToMidi.(note))
	});
	"loaded % stereo harp samples".format(~harpStereo.size).postln;
}, {
	"~harpStereo already loaded (% samples)".format(~harpStereo.size).postln;
});
)

// ---------------------------------------------------------------- SYNTHDEF
// Warp1 args: numChannels, bufnum, pointer(0..1), freqScale, windowSize,
// envbufnum(-1=default Hann), overlaps, windowRandRatio, interp.
(
SynthDef(\harpGrainWarp1, {
	|out=0, bufnum=0, amp=0.3, rate=1, gate=1, trans=1,
	 pos=0.5, posLfoRate=0.0, posLfoDepth=0.0,
	 grainDur=0.15, overlaps=8, rndRatio=0.02, envbuf= -1,
	 atk=0.02, dec=0.05, sus=0.7, rel=0.4|
	var env = EnvGen.kr(Env.adsr(atk, dec, sus, rel), gate, doneAction: 2);
	var lfo = LFTri.kr(posLfoRate, 0, posLfoDepth);
	var ptr = (pos + lfo).clip(0.02, 0.98);
	var sig = Warp1.ar(2, bufnum, ptr, rate * trans, grainDur, envbuf, overlaps, rndRatio, 2);
	Out.ar(out, sig * env * amp);
}).add;
)

// ---------------------------------------------------------------- EVENT TYPE
// NB: inside an event handler, currentEnvironment is the event itself, so
// bare `~harpStereo` would resolve to the event's own dict (nil). Reach
// across to topEnvironment[\harpStereo] explicitly.
(
Event.addEventType(\harpWarp1Event, { |e|
	var pcSampled = [0, 0, 2, 2, 4, 4, 6, 6, 8, 8, 10, 11];
	var pcOffset  = [0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,  0];
	var samples   = topEnvironment[\harpStereo];
	// \root: additive semitone shift (default 0). \octave: SC-convention
	// absolute octave (Event.default is 5); (~octave - 5) * 12 gives the
	// semitone offset, so an omitted \octave preserves the raw \note.
	var midi      = (~note + ~root + ((~octave - 5) * 12)).asInteger;
	var pc        = midi % 12;
	var oct       = midi.div(12);
	var target    = pcSampled[pc] + (oct * 12);
	var offset    = pcOffset[pc];
	var buf, found = false;
	samples.do({ |sample|
		if(sample.midiNote == target, { buf = sample.buffer });
	});
	if(buf.isNil, {
		[-1, 1, -2, 2, -3, 3].do({ |octDelta|
			if(found.not, {
				samples.do({ |sample|
					if(sample.midiNote == (target + (octDelta * 12)), {
						buf = sample.buffer;
						offset = offset - (octDelta * 12);
						found = true;
					});
				});
			});
		});
	});
	if(buf.notNil, {
		~bufnum     = buf;
		~rate       = offset.midiratio;
		~instrument = \harpGrainWarp1;
		~type       = \note;
		buf.postln;
		currentEnvironment.play;
	}, {
		"harpWarp1Event: no sample for midi %".format(~note).warn;
	});
});
)

// ---------------------------------------------------------------- EXAMPLE
(
Pdef(\harpWP,
	Pbind(
		\type,        \harpWarp1Event,
		\note,        Pseq([60, 64, 67, 72, 76, 72, 67, 64], inf),
		\root,        0,       // +2 semitones (additive)
		\octave,      4,       // 5 = no shift; 6 = +1 oct, 4 = -1 oct
		\trans,       8,       // post-selection playback-rate multiplier
		\dur,         0.2,
		\amp,         0.5,
		\pos,         Pwhite(0.0, 0.02),
		\posLfoRate,  Pwhite(0.01, 0.08),
		\posLfoDepth, 0.0,
		\grainDur,    0.03,//Pwhite(0.02, 0.08),
		\overlaps,    2,
		\rndRatio,    0.1,
		\atk, 0.05, \dec, 0.05, \sus, 0.7, \rel, 2.9
	);
).play(quant: 0);
)

Pdef(\harpWP).stop;

// ---------------------------------------------------------------- CLEANUP
// Pdef(\harpWP).clear;
// ~harpStereo.do({ |s| s.buffer.free }); ~harpStereo = nil;
s.meter
