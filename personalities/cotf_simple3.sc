/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note sine synths on ~beatClock; per-state octave, dur, and detune shifts; gesture maps to amp
sound:       pulsing sine pattern with per-state character; tuning slides drone in from below over ~20 s
pitch:       random choice from score voice pool, wrapped to a pitch class then transposed by \octave (per event via Pfunc)
rhythm:      note per \dur ~beatClock ticks (state sets dur: 0.125 idle, 0.5–1.0 piece, 0.75 tuning)
instruments: [Velaphone, Lumivox]
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var lastTime = 0;
var idleNotes = [0,2,4,5,7,5,4,2];
var tuneTime = 0;
//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.3;
m.rrateMassFilteredAttack = 0.999;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// Simple sine synth with ADSR + detune. Per-note lifetime (self-frees
// via Done.freeSelf), driven by the Pdef in ~init. `\detune` is a freq
// multiplier — analogous to `\ptch` on the harp's stereoSampler.
SynthDef(\simple, {|out=0, amp=0.0, freq=440, detune=1,
	attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = SinOsc.ar(freq * detune, 0, 1) ! 2;
	Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use{
		Pdef(m.ptn,
			Pbind(
				\instrument, \simple,
				\out, ob,
				\octave, 5,
				// \dur, 0.5,
				// Freq computed from the current voice pool wrapped to a pitch
				// class, then transposed into the current \octave register.
				// Bypasses default event's midinote calc to keep the mapping
				// obvious. Nil guards on both pool and octave — the first
				// event can fire before any Pdef.set from a state hook has
				// landed, and the event chain doesn't always inherit
				// default.octave (=5.0) reliably.
				\freq, Pfunc {
					var pool  = ~scoreVoicePool ? [69];
					var pitch = pool.choose.asInteger.wrap(0, 11);
					var oct   = (~octave ? 5).asInteger;
					(pitch + (12 * oct)).midicps
				},
				\args, #[],

			);
		);

		// Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		Pdef(m.ptn).set(\dur, 0.5);
		Pdef(m.ptn).play;
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
// ~next = {|d| };

//------------------------------------------------------------
// Room-state routing — Pdef stays playing across all states; per-state
// amp is set by the state-gated ticks (~idleNext etc.) so we only hear
// the synth during \piece. \silent handled one-shot here (no ~silentNext).
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { tuneTime = TempoClock.beats },
		\piece,   { },
		\curtain, { },
		\silent,  { Pdef(m.ptn).set(\amp, 0); }
	);
};

//------------------------------------------------------------
// State-gated ticks — same shape as cotf_harp1, minus the sample-only
// bits. Each fires at ~30 Hz while its state is current.
~idleNext = {|d|
	var amp = ((m.rrateMassFiltered) * 2.0).lincurve(0, 1.0, -60, -18, -4);
	Pdef(m.ptn).set(\octave, 3);
	Pdef(m.ptn).set(\freq, 200);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\dur, 0.125);
	// "idle".postln;
	// if (amp > -25, {
	// 	if (TempoClock.beats > (lastTime + 1), {
	// 		Pdef(m.ptn).set(\dur, 0.5);
	// 		lastTime = TempoClock.beats;
	// 	});
	// }, {
	// 	Pdef(m.ptn).set(\dur, 1);
	// });
};

~tuningNext = {|d|
	var amp = ((m.rrateMassFiltered) * 2.0).lincurve(0, 1.0, -60, -18, -4);
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\detune, 0.8);

	if ((TempoClock.beats - tuneTime) < 20, {
		Pdef(m.ptn).set(\detune, 1.0 + ((TempoClock.beats - tuneTime) / 25.0));
	});

	Pdef(m.ptn).set(\dur, 0.75);
};

~pieceNext = {|d|
	var amp = (m.accelMassFiltered + m.rrateMassFiltered).half.lincurve(0, 1.5, -70, -18, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 9, 1).asInteger;

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\detune, 1);

	if (amp > -30, {
		if (TempoClock.beats > (lastTime + 1), {
			Pdef(m.ptn).set(\dur, 0.5);
			lastTime = TempoClock.beats;
		});
	}, {
		Pdef(m.ptn).set(\dur, 1.0);
	});
};

~curtainNext = {|d|
	Pdef(m.ptn).set(\amp, -60.dbamp);
};

//------------------------------------------------------------
// Beat-aligned hooks. Empty stubs — fill in as ideas arise.
~onTick    = {|ctx| /* every subdivision */ };
~onHalf    = {|ctx| /* on half-bar change */ };
~onBeat    = {|ctx| /* every true beat */ };
~onBar     = {|ctx|
	// "simple6 onBar %  (sec % / phr %)".format(ctx.barIdx, ctx.sectionId, ctx.phraseId).postln;
};
~onPhrase  = {|ctx| /* on phrase change */ };
~onSection = {|ctx| /* on section change */ };
~onChord   = {|ctx| /* on chord change */ };
~onKey     = {|ctx| /* on key change */ };
~onScale   = {|ctx| /* on active_scale change */ };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(m.accelMassFiltered + m.rrateMassFiltered).half, m.accelMassFiltered];
};
