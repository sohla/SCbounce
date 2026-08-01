/*
gestures:    [beat, shake, tilt]
description: One long-lived LFTri+Sine synth holding a continuous drone, with a SECOND retriggerable envelope inside the same SynthDef — the "exciter". A Pdef on ~beatClock ticks a [2,1,1] figure, but the event handler only pushes \trig through when accelMassFiltered exceeds excThreshold. The tone always sings; move harder and punctuating attacks blossom over it. Both go through one RLPF, so the exciter is coloured by the same filter as the drone.
sound:       continuous filtered drone; harder motion adds noise + octave-up bursts on the beat. x tilt sweeps the cutoff.
pitch:       ~onBeat sets the drone from ctx.voicePool.choose wrapped to pitch class + baseMidi 60 + octave (piece sets octave from y tilt, -24..+24). Idle/curtain pick from ideleNotes by y tilt, NOT a rotation. Tuning holds 52 after a 15 s ramp.
rhythm:      drone is continuous. Exciter clock is Pseq([1,0.5,0.5] * 2) = [2,1,1] on ~beatClock; in idle accel scales the whole figure via \stretch (2x slower at rest, 2x faster moving), reset to 1 in the other states.
instruments: [Template]
*/

/*
- add compressor
- dynamics need some work
- accel on sub seq multiplier

*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var synth;
var baseMidi = 60; // C4
var lastTime = 0;
var tuneTime = 0;
var excThreshold = 0.3;   // accelMassFiltered must exceed this for the beat's exciter to fire
var octave = -12;
var ideleNotes = [45,47,50,52,57,59,62,64];  // idle/tuning/curtain — rotates on each half-bar

// Unique per-env event type — see cotf_harp1.sc for the scope-leak rationale.
var eventTypeName = (\exciterTick_ ++ m.ptn).asSymbol;

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.3;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// TWO envelopes in one long-lived synth:
//   lifetime = ADSR gated by \gate — controls the sustained tone amp.
//              Same shape as simple2. Held at sustain until ~deinit
//              sends \gate=0.
//   exciter  = perc envelope fired by \trig (t_trig control). Retriggers
//              each time the Pdef pushes \trig=1 through. Modulates a
//              brief noise + octave-up burst that gets added to the tone
//              BEFORE the filter — so the exciter's transient is coloured
//              by the same RLPF that shapes the drone.
SynthDef(\simple, {|out=0, amp=0.0, freq=440,
	attack=0.5, decay=0.03, sustain=0.8, release=1.0, gate=1,
	lagAttack=0.02, lagRelease=1.9, ffreq=440,
	excAttack=0.003, excRelease=0.18, excAmp=1|
	var lifetime = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var trig = \trig.tr(0);
	var exciter = EnvGen.kr(Env.perc(excAttack, excRelease), trig);
	var tone = LFTri.ar(freq, 0.2, 0.1) + SinOsc.ar(freq, 0, 1);
	var burst = (WhiteNoise.ar(0.2) + SinOsc.ar(freq * 2, 0, 0.9)) * exciter * excAmp;
	var filter = RLPF.ar(tone + burst, ffreq, 0.2).tanh * 0.3;
	Out.ar(out, filter!2 * lifetime * amp.lagud(lagAttack, lagRelease));
}).add;

//------------------------------------------------------------
// Pdef event type — the beat fires an event, this handler decides whether
// to push the trigger through to the long-lived synth. No synth spawn,
// just a side-effect on `synth`. Guard against synth being nil (Pdef could
// tick before ~init completes on a fast reload).
Event.addEventType(eventTypeName, {|e|
	if (synth.notNil and: { m.accelMassFiltered > excThreshold }) {
		synth.set(\trig, 1);
	};
});

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use{
		synth = Synth(\simple, [
			\out,     ob,
			\freq,    ((~scoreVoicePool.first.asInteger % 12) + baseMidi).midicps,
			\amp,     0,
			\attack,  0.5,
			\decay,   0.1,
			\sustain, 1.0,
			\release, 1.0,
			\ffreq,   440,
			\excAmp,  1,
		]);

		// Exciter clock — one event per ~beatClock quarter. The event's
		// handler (above) reads accel and either fires the synth's exciter
		// or does nothing. Steady quarter grid; \dur can be changed for
		// swing or subdivision variation.
		Pdef(m.ptn,
			Pbind(
				\type, eventTypeName,
				\dur,  Pseq([1,0.5,0.5,1,0.5,0.5,0.5,0.25,0.25,1,0.5,0.5] * 4, inf),
			);
		);
		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
	};

	// ~onResync in d.env (per-device dispatch). Body in topEnvironment.use
	// so ~beatClock resolves. No synths in a group here — nothing to freeAll,
	// the long-lived synth is unaffected by seek.
	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);
	synth.set(\gate, 0);
};

//------------------------------------------------------------
// State ticks — same shape as simple2. Each state sets the sustained tone's
// amp/filter/lag/pitch. \excAmp can be modulated per state too — piece
// gets a louder exciter, curtain a quieter one.
~idleNext = {|d, ctx|
	var amp = (m.accelMass + m.rrateMass).lincurve(0, 1.0, -90, -15, 4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 200, 600, 3);
	var idx = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 0, ideleNotes.size, 1).asInteger;
	var la = (m.accelMassFiltered).lincurve(0, 2.0, 0.9, 0.02, -1);


	synth.set(\amp, amp.dbamp);
	synth.set(\lagAttack, la);
	synth.set(\lagRelease, 0.8);
	synth.set(\ffreq, ffreq);
	synth.set(\excAmp, 0.9);   // quieter exciter in idle

	// Accel drives the pattern speed. \stretch scales \dur (delta = dur *
	// stretch), so the [2,1,1] shape survives and just runs faster. 0.3 is
	// where accelMass sits at rest — gravity, not motion.
	Pdef(m.ptn).set(\stretch, m.accelMassFiltered.lincurve(0.3, 2.0, 5.0, 0.5, -2));

	if(TempoClock.beats > (lastTime + 0.6),{
		{synth.set(\freq, (ideleNotes[idx] + 24).midicps)}.defer(0.4);
		lastTime = TempoClock.beats;
	});
};

~tuningNext = {|d, ctx|
	var amp = (m.accelMass + m.rrateMass).lincurve(0, 1.0, -70, -8, 4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 200, 800, 3);
	var fmod = ((d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-3.0,3.0,1));
	var tt = 15.0;
	var la = (m.accelMassFiltered).lincurve(0, 2.0, 0.9, 0.02, -1);

	if( (TempoClock.beats-tuneTime) < tt, {
		var val = (TempoClock.beats-tuneTime) / tt;
		tuneTime = TempoClock.beats;
		synth.set(\freq, (64 + (val.lincurve(0, 1, 1, 0.0001,-2) * 7)).midicps);
	},{
		synth.set(\freq, 64.midicps);
	});

	synth.set(\amp, amp.dbamp);
	synth.set(\lagAttack, la);
	synth.set(\lagRelease, 2.1);
	synth.set(\ffreq, ffreq);
	synth.set(\excAmp, 0.3);
	Pdef(m.ptn).set(\stretch, 1);   // clear idle's speed
};

~pieceNext = {|d, ctx|
	var amp = (m.accelMassFiltered).lincurve(0, 1.1, -90, -6, -1);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 500, 8000, 1);
	var la = (m.accelMassFiltered).lincurve(0, 2.0, 0.9, 0.02, -1);

	octave = ((d.sensors.gyroEvent.y / pi.half).linlin(-1.0,1.0,0,2).asInteger * 12) ;
	
	synth.set(\amp, amp.dbamp);
	synth.set(\lagAttack, la);
	synth.set(\lagRelease, 1.9);
	synth.set(\ffreq, ffreq);
	synth.set(\excAmp, 1.0);   // full exciter in piece
	Pdef(m.ptn).set(\stretch, 1);   // clear idle's speed
};

~curtainNext = {|d, ctx|
	var amp = (m.accelMass + m.rrateMass).lincurve(0, 1.0, -70, -30, 4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 300, 500, 3);
	synth.set(\freq, 57.midicps);
	synth.set(\ffreq, ffreq);
	synth.set(\amp, amp.dbamp);
	synth.set(\lagAttack, 0.6);
	synth.set(\lagRelease, 2.0);
	synth.set(\excAmp, 0.4);
	Pdef(m.ptn).set(\stretch, 1);   // clear idle's speed
};

//------------------------------------------------------------
// Beat-locked pitch — half-bar updates from voicePool (same as simple2).
~onTick    = {|ctx| 
};
~onHalf    = {|ctx|
};
~onBeat    = {|ctx| 
	s.bind {
		synth.set(\freq,
			((ctx.voicePool.choose.asInteger % 12) + baseMidi + octave).midicps);
	};
};
~onBar     = {|ctx| 
};
~onPhrase  = {|ctx| };
~onSection = {|ctx| };
~onChord   = {|ctx| };
~onKey     = {|ctx| };
~onScale   = {|ctx| };

//------------------------------------------------------------
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { tuneTime = TempoClock.beats },
		\piece,   { },
		\curtain, { },
		\silent,  { synth.set(\amp, 0); }
	);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = {|d,p| [(d.sensors.gyroEvent.y / pi.half)] };
