/*
gestures:    [beat, shake]
description: multiBeat1 with a voice that never stops. The pattern is multiBeat1's, unchanged — the subdivision ladder, the Pswitch group gate, the same \multiBeatVoice, the same marks — and under it sits one held instance of the same voice, gated instead of percussive, so there is always sound whether or not the stick is moving. The drone's amp has a floor, so it never stops, and its pitch is taken straight off the pattern — every note the figure plays is handed to the drone down at octave 2 and glided into, so the held voice traces the line the struck one is playing. Accel is left owning level, filter and the subdivision ladder, and nothing else.
sound:       short filtered pulse+sine mallet tone over a held, gliding version of itself
pitch:       pattern: first n notes of a local pool. drone: whatever the pattern just played, note + root, at octave 2
rhythm:      n notes per beat, n from accel. binary 1/2/4/8 and triplet 3/6 on one continuous ladder. The drone has no rhythm — that is the point of it
instruments: [Template]
*/

var m = ~model;
var group;
var drone;

// STATE, and the only thing here that is one. droneNote is the pitch the
// pattern last handed the drone; ~onEvent writes it and the ring's draw func
// reads it. It is kept because it genuinely CANNOT be recomputed — it comes
// out of the pattern's note stream, and nothing in m knows where inside a
// group the figure currently is. Everything else this file needs is computed
// where it is used, not named up here. It starts two octaves up from zero
// and is real from the pattern's first note.
var droneNote = 12 * 2;

// THE RULE, in one line each.
//
// divs is the ladder of subdivisions, ordered by density so accel can
// ride straight up it. For each n:
//
//   Pn(n, n)             -> n copies of n      : the dur denominator, latched for the group
//   Pseries(0, 1, n)     -> 0 .. n-1           : where we are inside the group
//   Pseq(pool.keep(n),1) -> first n pool notes : "each subdivision has that multiple of values"
//
// All three are FINITE patterns of length n, so all three Pswitches end
// their group on the same event and re-read \divIdx together. That
// lockstep is what keeps the figure coherent — drop it and the note
// count and the dur can disagree.
//
// Take 3 and 6 out of divs for a binary-only ladder.
var divs = [1, 2, 4];
var pool = [0, 4, 7, 11, 12, 11, 7, 2];   // needs at least divs.last entries

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeatVoice, {|out=0, freq=440, amp=0.2, pan=0,
    attack=0.005, release=0.4, ffreq=3000|
	var env = EnvGen.kr(Env.perc(attack, release), doneAction: 2);
	var sig = Pulse.ar(freq, 0.3, 0.5) + SinOsc.ar(freq / 2, 0, 0.6);
	sig = RLPF.ar(sig, ffreq.clip(80, 12000), 0.4);
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

// The same voice, held : identical oscillators and filter, an ASR on a
// gate in place of the perc env, and lags on freq and ffreq so it glides
// between the pattern's notes instead of stepping. lag is short enough
// to arrive inside a fast subdivision — raise it and the drone stops
// tracking the line and hovers at its average.
SynthDef(\multiBeatDrone, {|out=0, freq=110, amp=0.05, pan=0,
    attack=1.0, release=0.8, ffreq=1200, gate=1, lag=0.1|
	var env = EnvGen.kr(Env.asr(attack, 1, release, \sin), gate, doneAction: 2);
	var f = freq.lag(lag);
	var sig = Pulse.ar(f, 0.3, 0.5) + SinOsc.ar(f / 2, 0, 0.6);
	sig = RLPF.ar(sig, ffreq.lagud(0.1,0.3).clip(80, 12000), 0.4);
	Out.ar(out, Pan2.ar(sig, pan, amp.lagud(0.08,0.9) * env));
}).add;

//------------------------------------------------------------
// visual : the subdivision, drawn as itself. Each group lays n marks
// left to right across the canvas over one beat, so the density you
// hear is the density you see, and a triplet reads as three marks where
// a sixteenth run reads as four. Atlas grammar G8 (event-triggered)
// over a G3 grid field.
//
//   step in group -> horizontal position   (\sx -> \ex)
//   amp           -> mark size
//   dur           -> how long the mark lives
~init = ~init <> {|d|
	group = Group.new;

	// At rest : the amp floor and the filter's closed end, written out here
	// rather than fetched from a named function. ~next takes both over on its
	// first tick ~10ms later, and ~onEvent takes the pitch on the first note.
	drone = Synth(\multiBeatDrone, [
		\amp, 0.025, \freq, droneNote.midicps, \ffreq, 500, \gate, 1
	], group);

	//--------------------------------------------------------
	// visual : the drone, as the ground the marks are struck against.
	// ONE held event — duration: inf, so the cull never expires it and
	// normTime stays pinned at 0, which means every start*->end* blend
	// the core would do is frozen. The draw func does that blending
	// itself, so the ring swells with the gesture and changes shape with
	// the line. clearEvents (personality unload) is what removes it.
	//
	// It RECOMPUTES the drone's amp from m rather than being handed it —
	// the same expression ~next sends to the synth, evaluated here at frame
	// rate off the same source, so the two cannot drift a tick apart. The
	// event owns the bounds (ampFloor, ampCeil, noteLo/Hi, lobeMin/Max) and
	// the model picks the point inside them, which is the only way a held
	// event can carry live values at all.
	//
	// Lineage: the concentric-ring cyclic notations, read as a ground
	// rather than as a clock. Atlas grammar G8.
	//
	//   drone amp  -> radius, between startSize and endSize
	//   drone note -> number of lobes on the ring
	~vdef.(\droneRing, { |ev, c|
		var n = ev[\numPoints] ? 96;
		var mod = ev[\modulation] ? ();
		var ampMin = mod[\ampFloor] ? 0.025;
		var ampMax = mod[\ampCeil] ? 0.3;
		var noteLo = mod[\noteLo] ? 22;
		var noteHi = mod[\noteHi] ? 40;
		var lobeMin = mod[\lobeMin] ? 2;
		var lobeMax = mod[\lobeMax] ? 9;
		var depth = mod[\lobeDepth] ? 0.08;
		var amp = m.accelMassFiltered.lincurve(0, 1.0, -32, -11, -1).dbamp.max(ampMin);
		var open = amp.linlin(ampMin, ampMax, 0.0, 1.0);
		var lobes = droneNote.linlin(noteLo, noteHi, lobeMin, lobeMax).round.asInteger;
		var radius = ev[\startSize].blend(ev[\endSize], open);
		Array.fill(n, { |i|
			var angle = i / n * 2pi;
			var r = radius * (1 + (depth * sin(angle * lobes)));
			c[\pos] + Polar(r, angle).asPoint
		})
	});

	(
		type: \customVisualEvent,
		amp: 0,
		dur: 0.01,
		viewID: d.port,
		shape: \droneRing,
		fill: false,
		startSize: 70,
		endSize: 300,
		startWidth: 1,
		endWidth: 1,
		startColor: Color.new(0.2, 0.55, 0.5, 0.55),
		endColor: Color.new(0.2, 0.55, 0.5, 0.55),
		sx: 0, sy: 0, ex: 0, ey: 0,
		duration: inf,
		modulation: (
			ampFloor: 0.025,
			ampCeil: 0.3,
			noteLo: 22,
			noteHi: 40,
			lobeMin: 2,
			lobeMax: 9,
			lobeDepth: 0.08,
			amp: 0,
		),
	).play;

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatVoice,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx)),
			\root, Pseq([0,3,-2,1].stutter(32), inf),
			\octave, Prand([4,5,6], inf),
			\dur,  Pkey(\div).reciprocal * 0.5,
			// \release, Pkey(\dur) * 1.8,
			\pan, Pwhite(-0.2, 0.2),

			\type, \customVisualEvent,
			\shape, \circle,
			\sx, (Pkey(\step) / Pkey(\div) * 1.6) - 0.8,
			\ex, Pkey(\sx),
			\startSize, Pkey(\amp) * 400,
			\endSize, Pkey(\amp) * 8,
			\startColor, Color.new(0.6, 1.0, 0.8),
			\endColor, Color.new(0.1, 0.4, 0.6).alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 8,
			\func, Pfunc({|e| ~onEvent.(e)}),

			\args, #[],
		)
	);

	// Seed the envir — ~next has not run when the first events fire, and
	// a nil \divIdx would index the Pswitch lists with nil.
	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\ffreq, 2000);

	Pdef(m.ptn).play(quant: 1);
};

//------------------------------------------------------------
// The drone is released rather than freed, and the group waits for it —
// a hard freeAll on a held voice is a click.
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	if (drone.notNil) { drone.set(\gate, 0) };
	fork {
		0.9.wait;
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		drone = nil;
	};
};

//------------------------------------------------------------
// The pattern hands the drone its pitch here, on the note that just
// sounded — \note and \root, at octave 2 over the pattern's base of 36.
// The SynthDef's lag is what turns that step into a glide, so the drone
// traces the figure instead of clicking through it.
~onEvent = {|e|

	m.com.root = e.root;
	m.com.dur = e.dur;

	droneNote = (e[\note] ? 0) + (e[\root] ? 0) + (12 * 2) + 36;
	if (drone.notNil) { drone.set(\freq, droneNote.midicps) };
};

//------------------------------------------------------------
// Accel drives the ladder index, and nothing else touches it — \divIdx
// is deliberately NOT a Pbind key, because a Pbind key would override
// the envir and defeat this .set. It also sets the drone's level and
// filter — but not its pitch, which is the pattern's job, in ~onEvent.
~next = {|d|
	var idx = m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 1.0, -40, -10, -1);
	var ffreq = m.accelMassFiltered.lincurve(0, 1.0, 700, 6000, 2);
	var rel = m.accelMassFiltered.lincurve(0, 1.0, 0.1, 1.2, 2);

	// The drone's own level and filter. The amp floor is what makes this
	// file what it is : clamped above zero, so the synth is always sounding.
	var droneAmp = m.accelMassFiltered.lincurve(0, 2.0, -32, -10, -1);
	var droneFfreq = m.accelMassFiltered.lincurve(0, 1.0, 500, 3000, 2);

	if(droneAmp < 31.neg, { droneAmp = 90.neg });
	if(amp < 39.neg, { amp = 90.neg });

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\attack, 0.0003);
	Pdef(m.ptn).set(\release, rel);


	if (drone.notNil) {
		drone.set(
			\amp, droneAmp.dbamp,
			\ffreq, droneFfreq
		);
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass, m.accelMassFiltered,
		m.accelMassFiltered.lincurve(0, 1.0, -32, -11, -1).dbamp.max(0.025)];
};
