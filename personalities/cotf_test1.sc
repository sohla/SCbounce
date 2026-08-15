/*
gestures:    [beat, shake, tilt]
description: Sampler with a layered trigger design — a running Pdef with rotation-driven subdivision, plus accel-threshold one-shots punching through on top
sound:       harp samples; running pattern with gestural accents; rotation-driven subdivision
pitch:       local voice pool wrapped to pitch class; tilt-driven octave
rhythm:      running Pdef with rotation-density \dur + accel-threshold one-shots layered on top
instruments: [Aetherharp]
*/

var m = ~model;
var lastTime = 0;     // throttle anchor for one-shots
var group;            // dedicated Group

// the conductor used to hand pitch material down as ctx.voicePool.
// standalone, the pool is local.
var voicePool = [0, 2, 4, 7, 9, 11];

//------------------------------------------------------------
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

var folder = PathName("~/Music/cotf_samples/harp");
var samplesLib;

// Unique per-env event type.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------
// Filter tuning.
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.3;
m.rrateMassFilteredAttack = 0.999;
m.rrateMassFilteredDecay  = 0.6;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
// Sampler SynthDef — shared shape across the cotf samplers.
// \freq is passed through (customEvent computes it) so the SynthDef can
// drive freq-tracked oscillators / filters / subs.
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, freq=440, ptch=1, start=0, pan=0,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1|
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// visual : two marks for two layers. the running pattern draws small
// circles across the canvas by pitch; the gestural one-shots draw a big
// bright cross on top, so an accent is visibly a different event rather
// than a louder one. Atlas grammar G8 (event-triggered).
//
//   pitch class -> horizontal position   (\sx -> \ex)
//   octave      -> vertical position     (\sy -> \ey)
//   amp         -> mark size
//   one-shot    -> cross instead of circle, wider and brighter
~init = ~init <> {

	// Standard even-MIDI library lookup with odd-note detune.
	var findSampleBuffer = {|note|
		var bufnum;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{ bufnum = sample.buffer });
		});
		bufnum
	};

	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading sample : % \n", path.fileNameWithoutExtension);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	// Standard customEvent — odd/even fallback resample. Used both by
	// the running Pdef and by the inline one-shot events in ~next.
	// Hands on to \customVisualEvent, which draws the mark and re-types
	// to \note itself.
	Event.addEventType(eventTypeName, {|e|
		~note = (~note + ~root + (12 * ~octave)).asInteger;
		// Send the true playing pitch to the SynthDef.
		~freq = ~note.midicps;
		if(~note.odd, {
			~bufnum = findSampleBuffer.(~note - 1);
			~rate = 1.midiratio;
		}, {
			~bufnum = findSampleBuffer.(~note);
			~rate = 1;
		});
		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;

	// Running-Pdef backbone. Pbind holds ONLY static routing — every
	// param (including \root) is set inline from ~next.
	Pdef(m.ptn,
		Pbind(
			\instrument, \stereoSampler,
			\group, group,   // route into personality group
			\type, eventTypeName,
			\note, 0,

			\shape, \circle,
			\sx, Pfunc{|e| (e[\root] ? 0).wrap(0, 11).linlin(0, 11, -0.8, 0.8) },
			\ex, Pkey(\sx),
			\sy, Pfunc{|e| (e[\octave] ? 5).linlin(3, 7, 0.6, -0.6) },
			\ey, Pkey(\sy),
			\startSize, Pfunc{|e| (e[\amp] ? 0.2).linlin(0, 1, 30, 140) },
			\endSize, 8,
			\startColor, Color.new(0.7, 0.9, 1.0),
			\endColor, Color.new(0.2, 0.4, 0.8).alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, 0.9,
		);
	);

	Pdef(m.ptn).play(quant: 4);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\dur, 1);
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\ptch, 1);
};

//------------------------------------------------------------
// Idempotent cleanup — notNil guards for double-~deinit safety.
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
// LAYERED. Running Pdef with rotation-density on \dur, tilt-driven
// octave, expressive amp curve. Accel-threshold one-shots layered on top
// with a slightly higher threshold + louder so they punch through the
// running pattern.
// Two reused values (pdefAmp, oct) captured as local vars because the
// one-shot block below relies on them.
~next = {|d|
	var pdefAmp = m.accelMassFiltered.lincurve(0, 1.5, -70, -8, -1);
	var oct     = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 7, 1).asInteger;

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\dur,    m.rrateMassFiltered.lincurve(0.01, 1.0, 2.0, 0.25, -4));
	Pdef(m.ptn).set(\amp,    pdefAmp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\ptch,   1);
	Pdef(m.ptn).set(\root,   voicePool.choose.wrap(0, 11).asInteger);

	// Gestural one-shot on top — higher threshold to reserve for
	// stronger hits, slightly louder to sit above the pattern.
	if (m.accelMassFiltered > 0.7, {
		if (TempoClock.beats > (lastTime + 0.3), {
			var root = voicePool.choose.wrap(0, 11).asInteger;
			(
				instrument: \stereoSampler,
				type:       eventTypeName,
				group:      group,
				note:       0,
				root:       root,
				octave:     oct + 1,
				amp:        (pdefAmp + 3).dbamp,
				ptch:       1.0,

				viewID:     d.port,
				shape:      \cross,
				sx:         root.linlin(0, 11, -0.8, 0.8),
				ex:         root.linlin(0, 11, -0.8, 0.8),
				sy:         (oct + 1).linlin(3, 7, 0.6, -0.6),
				ey:         (oct + 1).linlin(3, 7, 0.6, -0.6),
				startSize:  220,
				endSize:    20,
				startColor: Color.white,
				endColor:   Color.new(0.2, 0.4, 0.8).alpha_(0.0),
				startWidth: 6,
				endWidth:   0.5,
				duration:   1.4,
			).play;
			lastTime = TempoClock.beats;
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p|
	[m.accelMassFiltered, m.rrateMassFiltered];
};
