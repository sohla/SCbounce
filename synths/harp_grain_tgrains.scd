/*
Grain topology 1 of 3: TGrains cloud
-------------------------------------
Per-event grain-cloud synth. Each pattern event fires a fresh synth whose
bufnum is picked from the harp library based on ~note, with an ADSR
envelope so the synth self-frees on note release.

TGrains treats bufnum as MONO — a stereo buffer would read as interleaved
L/R samples (near-Nyquist garbage). Samples are loaded as channel-0 mono
via Buffer.readChannel.

Sample coverage: pitch classes {0,2,4,6,8,10,11}. Missing pcs {1,3,5,7,9}
map to nearest-lower sampled pc with a semitone rate bend. Out-of-octave
notes fall back to nearest available octave with rate compensation.

Pattern keys for pitch: \note (raw MIDI, e.g. 60 = middle C), plus
\root (additive semitone shift, default 0) and \octave (SC convention,
default 5 = no shift; 6 = +1 octave, 4 = -1 octave). \trans is a
post-selection playback-rate multiplier — separate lever from \note.
*/

// ---------------------------------------------------------------- LOADER
(
if(~harpMono.isNil, {
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
	~harpMono = PathName("~/Music/cotf_samples/harp").entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buf  = Buffer.readChannel(s, path.fullPath, channels: [0]);
		(name: path.fileNameWithoutExtension, buffer: buf, midiNote: noteToMidi.(note))
	});
	"loaded % mono harp samples".format(~harpMono.size).postln;
}, {
	"~harpMono already loaded (% samples)".format(~harpMono.size).postln;
});
)

// ---------------------------------------------------------------- SYNTHDEF
(
SynthDef(\harpGrainTGrains, {
	|out=0, bufnum=0, amp=0.3, rate=1, gate=1, trans=1, freq=440,
	 pos=0.5, density=20, grainDur=0.15,
	 panSpread=0.6, rateSpread=0.01,
	 atk=0.02, dec=0.05, sus=0.7, rel=0.4|
	var env       = EnvGen.kr(Env.adsr(atk, dec, sus, rel), gate, doneAction: 2);
	var trig      = Dust.kr(density);
	var centerPos = pos.clip(0.02, 0.98) * BufDur.kr(bufnum);
	var grate     = rate * trans * TRand.kr(1 - rateSpread, 1 + rateSpread, trig);
	var gpan      = TRand.kr(panSpread.neg, panSpread, trig);
	var sig       = TGrains.ar(2, trig, bufnum, grate, centerPos,
	                           grainDur, gpan, 1, 2);
	var sub = FreqShift.ar(sig, freq * 0.5, 0, 0.4);
	Out.ar(out, (sig + sub) * env * amp);
}).add;
)

// ---------------------------------------------------------------- EVENT TYPE
// NB: inside an event handler, currentEnvironment is the event itself, so
// bare `~harpMono` would resolve to the event's own dict (nil). Reach
// across to topEnvironment[\harpMono] explicitly.
(
Event.addEventType(\harpTGrainsEvent, { |e|
	var pcSampled = [0, 0, 2, 2, 4, 4, 6, 6, 8, 8, 10, 11];
	var pcOffset  = [0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,  0];
	var samples   = topEnvironment[\harpMono];
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
		~freq       = midi.midicps;
		~instrument = \harpGrainTGrains;
		~type       = \note;
		currentEnvironment.play;
	}, {
		"harpTGrainsEvent: no sample for midi %".format(~note).warn;
	});
});
)

// ---------------------------------------------------------------- EXAMPLE
(
Pdef(\harpTG,
	Pbind(
		\type,       \harpTGrainsEvent,
		\note,       Pseq([60, 64, 67, 72, 67, 64, 60, 55], inf),
		\root,       0,       // additive semitone shift
		\octave,     4,       // 5 = no shift; 6 = +1 oct, 4 = -1 oct
		\trans,      1,       // post-selection playback-rate multiplier
		\dur,        0.2,
		\amp,        0.8,
		\density,    100,//Pwhite(15, 400),
		\grainDur,   0.07,//Pwhite(0.1,0.3),
		\pos,        Pwhite(0.01, 0.07),
		\panSpread,  1,
		\rateSpread, 0.02,
		\atk, 0.004, \dec, 0.2, \sus, 0.3, \rel, 0.4
	);
).play(quant: 0);
)

Pdef(\harpTG).stop;

// ---------------------------------------------------------------- CLEANUP
// Pdef(\harpTG).clear;
// ~harpMono.do({ |s| s.buffer.free }); ~harpMono = nil;
