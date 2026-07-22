/*
Grain topology 3 of 3: GrainBuf with custom envelope buffers
------------------------------------------------------------
Per-event grain-cloud synth. Same TGrains-flavour setup (Dust-triggered
mono grains) but uses GrainBuf, which lets you point at a custom
envelope BUFFER for grain-window shape — Hann (default), percussive
(sharp attack, exp decay), reverse-percussive, boxcar (hard edges).
That's the sculpt dimension TGrains doesn't give you.

Same mono buffer set as file 1 (~harpMono); loader is skipped if already
populated. Four grain-envelope buffers (~grainEnvHann, ~grainEnvPerc,
~grainEnvRev, ~grainEnvBox) are built the first time this file runs.

Pattern keys for pitch: \note (raw MIDI, e.g. 60 = middle C), plus
\root (additive semitone shift, default 0) and \octave (SC convention,
default 5 = no shift; 6 = +1 octave, 4 = -1 octave). \trans is a
post-selection playback-rate multiplier — separate lever from \note.
*/

// ---------------------------------------------------------------- LOADER (samples)
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

// ---------------------------------------------------------------- LOADER (grain envelopes)
// Four 1024-sample envelope buffers. GrainBuf's envbufnum arg reads
// these to shape each grain. -1 = built-in Hann (default fallback).
(
if(~grainEnvHann.isNil, {
	fork {
		~grainEnvHann = Buffer.alloc(s, 1024, 1);
		~grainEnvPerc = Buffer.alloc(s, 1024, 1);
		~grainEnvRev  = Buffer.alloc(s, 1024, 1);
		~grainEnvBox  = Buffer.alloc(s, 1024, 1);
		s.sync;
		~grainEnvHann.sendCollection(Env([0, 1, 0], [0.5, 0.5], \sine).discretize(1024));
		~grainEnvPerc.sendCollection(Env([0, 1, 0], [0.05, 0.95], [4, -4]).discretize(1024));
		~grainEnvRev .sendCollection(Env([0, 1, 0], [0.95, 0.05], [-4, 4]).discretize(1024));
		~grainEnvBox .sendCollection(Env([0, 1, 1, 0], [0.02, 0.96, 0.02], \lin).discretize(1024));
		"loaded 4 grain-env buffers: ~grainEnvHann ~grainEnvPerc ~grainEnvRev ~grainEnvBox".postln;
	};
}, {
	"grain-env buffers already loaded".postln;
});
)

// ---------------------------------------------------------------- SYNTHDEF
// GrainBuf args: numChannels, trigger, dur, sndbuf, rate, pos,
// interp(1|2|4), pan, envbufnum(-1=Hann).
(
SynthDef(\harpGrainGrainBuf, {
	|out=0, bufnum=0, amp=0.3, rate=1, gate=1, trans=1,
	 pos=0.5, density=20, grainDur=0.15,
	 panSpread=0.6, rateSpread=0.01,
	 envbuf= -1, interp=4,
	 atk=0.02, dec=0.05, sus=0.7, rel=0.4|
	var env   = EnvGen.kr(Env.adsr(atk, dec, sus, rel), gate, doneAction: 2);
	var trig  = Dust.kr(density);
	var grate = rate * trans * TRand.kr(1 - rateSpread, 1 + rateSpread, trig);
	var gpan  = TRand.kr(panSpread.neg, panSpread, trig);
	var gpos  = pos.clip(0.02, 0.98);
	var sig   = GrainBuf.ar(2, trig, grainDur, bufnum, grate, gpos, interp, gpan, envbuf);
	Out.ar(out, sig * env * amp);
}).add;
)

// ---------------------------------------------------------------- EVENT TYPE
// NB: inside an event handler, currentEnvironment is the event itself, so
// bare `~harpMono` would resolve to the event's own dict (nil). Reach
// across to topEnvironment[\harpMono] explicitly.
(
Event.addEventType(\harpGrainBufEvent, { |e|
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
		~instrument = \harpGrainGrainBuf;
		~type       = \note;
		currentEnvironment.play;
	}, {
		"harpGrainBufEvent: no sample for midi %".format(~note).warn;
	});
});
)

// ---------------------------------------------------------------- EXAMPLE
// Swap \envbuf between ~grainEnvHann / ~grainEnvPerc / ~grainEnvRev /
// ~grainEnvBox to hear the grain-window difference. Perc/Rev give
// clear per-grain articulation, Box adds click at grain edges.
(
Pdef(\harpGB,
	Pbind(
		\type,       \harpGrainBufEvent,
		\note,       Pseq([60, 64, 67, 72, 67, 64, 60, 55], inf),
		\root,       0,       // additive semitone shift
		\octave,     5,       // 5 = no shift; 6 = +1 oct, 4 = -1 oct
		\trans,      1,       // post-selection playback-rate multiplier
		\dur,        0.2,
		\amp,        0.6,
		\density,    Pwhite(15, 35),
		\grainDur,   Pwhite(0.05, 0.2),
		\pos,        Pwhite(0.02, 0.03),
		\envbuf,     ~grainEnvHann,   // try ~grainEnvPerc / ~grainEnvRev / ~grainEnvBox
		\interp,     4,
		\panSpread,  0.6,
		\rateSpread, 0.02,
		\atk, 0.05, \dec, 0.05, \sus, 0.7, \rel, 0.4
	);
).play(quant: 0);
)

Pdef(\harpGB).stop;

// ---------------------------------------------------------------- CLEANUP
// Pdef(\harpGB).clear;
// [~grainEnvHann, ~grainEnvPerc, ~grainEnvRev, ~grainEnvBox].do(_.free);
// ~grainEnvHann = nil; ~grainEnvPerc = nil; ~grainEnvRev = nil; ~grainEnvBox = nil;
// ~harpMono.do({ |s| s.buffer.free }); ~harpMono = nil;
