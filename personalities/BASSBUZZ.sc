/*
gestures:    [beat, shake, tilt]
description: One long-lived Saw + Sine synth through a resonant LP filter, running for the whole personality life. No pattern, no per-note synths — every state just re-shapes the same voice's amp, cutoff and lag character. Gesture maps to amp (accelMass + rrateMass) and cutoff (x tilt).
sound:       warm low drone with a tilt-swept resonant filter; sits two octaves below the score, so it reads as bass
pitch:       ~onHalf sets the drone from ctx.voicePool.first wrapped to pitch class + baseMidi 60 - 24 (two octaves down). In idle, y tilt selects from a fixed ideleNotes array. Tuning bends to 57 over 15 s, with the bend depth set by y tilt. Curtain pins 57.
rhythm:      none — continuous drone. The idle note only changes when y tilt moves it, rate-limited to one change per 0.2 s.
instruments: [gymBro]
*/


/*
- make this more like simle4 ? OR make simple4 more like simple2?
- less dynamic gestures
- more variation on filter
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var synth;
// Wrap ~scoreVoicePool pitches into a single octave starting here (MIDI).
var baseMidi = 60; // C4
var lastTime = 0;
var tuneTime = 0;

// var ideleNotes = [45,49,52,57,52,49,45,46,50,53,58,53,50,46,47,51,54,59,54,51,47,46,50,53,58,53,50,46];
var ideleNotes = [45,49,52,57];
// var ideleNotes = [45,80];

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1, lagAttack=0.02, lagRelease=1.9, ffreq = 440|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = Saw.ar(freq,0.2,0.1) + SinOsc.ar(freq/2,0,0.1);
	var filter = RLPF.ar(sig, ffreq.lag(0.3), 0.2) * 0.5;
    Out.ar(out, filter!2 * env * amp.lagud(lagAttack, lagRelease));
}).add;

//------------------------------------------------------------

~init = ~init <> {
	topEnvironment.use{
		synth = Synth(\simple, [
			\out, ob,
			\freq,    ((~scoreVoicePool.first.asInteger % 12) + baseMidi).midicps,
			\amp,     0,
			\attack,  0.5,
			\decay,   0.1,
			\sustain, 1.0,
			\release, 1.0,
			\ffreq,   440,
		]);
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
// State-gated tick hooks. Each fires at IMU rate (~30 Hz) in addition to
// ~next, only while the matching ~roomState is current. Throttled postlns
// (once/sec) so we can see the routing without spamming the post window.
~idleNext = {|d, ctx|

	var amp = (m.accelMass + m.rrateMass).lincurve(0, 1.0, -80, -10, 4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 20, 6000, -3);
	var dur = (m.accelMass + m.rrateMass).lincurve(0, 1.0, 2,0.1, 4);
	var idx = (d.sensors.gyroEvent.y / pi.half).linlin(-1, 1, 0, ideleNotes.size).asInteger;

	synth.set(\amp, amp.dbamp);
	synth.set(\lagAttack, 0.2);
	synth.set(\lagRelease, 1.1);
	synth.set(\ffreq, ffreq);
	

	if(TempoClock.beats > (lastTime + 0.2),{
		// ideleNotes = ideleNotes.rotate(-1);
		{synth.set(\freq, (ideleNotes[idx]).midicps)}.defer(0.4);
		lastTime = TempoClock.beats;
	
	});
};

~tuningNext = {|d, ctx|

	var amp = (m.accelMass + m.rrateMass).lincurve(0, 1.0, -70, -3, 4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 200, 800, 3);
	var fmod = ((d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-12.0,28.0,1));
	var tt = 20.0;

	if( (TempoClock.beats-tuneTime) < tt, {
		var val = (TempoClock.beats-tuneTime) / tt;
		synth.set(\freq, (57 + (val.linexp(0, 1, 1, 0.0001) * fmod)).midicps);
	},{
		synth.set(\freq, 57.midicps);

	});

	synth.set(\amp, amp.dbamp);
	synth.set(\lagAttack, 0.4);
	synth.set(\lagRelease, 2.1);
	synth.set(\ffreq, ffreq);
	
	if(amp < -69, {
		if(TempoClock.beats > (lastTime + 1),{
			lastTime = TempoClock.beats;
		});
	});


};

~pieceNext = {|d, ctx|

	var amp = (m.accelMass + m.rrateMass).lincurve(0, 1.6, -90, 2, -1);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 500, 12000, 3);
	synth.set(\amp, amp.dbamp);
	synth.set(\lagAttack, 0.002);
	synth.set(\lagRelease, 0.9);
	synth.set(\ffreq, ffreq);

};

~curtainNext = {|d, ctx|

	var amp = (m.accelMass + m.rrateMass).lincurve(0, 1.0, -70, -30, 4);
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 300, 500, 3);
	synth.set(\freq, 57.midicps);
	synth.set(\ffreq, ffreq);
	synth.set(\amp, amp.dbamp);
	synth.set(\lagAttack, 0.6);
	synth.set(\lagRelease, 2.0);
};

//------------------------------------------------------------
// Beat-locked pitch. ctx.voicePool is the current half's MIDI pitches
// (populated by the score Routine from p["fitted_notes"]). Hook body
// runs inside d.env.use, so we can't reach ~scoreVoicePool directly —
// ctx carries everything the hook needs. s.bind so the /n_set lands on
// the same s.latency timeline as the audio.
~onTick = {|ctx|
};
~onHalf    = {|ctx|
	s.bind {
		synth.set(\freq,
			((ctx.voicePool.first.asInteger % 12) + baseMidi - 24).midicps);
	};
};
~onBeat    = {|ctx| 
};
~onBar     = {|ctx| 
};
~onPhrase  = {|ctx| /* on phrase change */ };
~onSection = {|ctx| /* on section change */ };
~onChord   = {|ctx| /* on chord change */ };
~onKey     = {|ctx| /* on key change */ };
~onScale   = {|ctx| /* on active_scale change */ };

//------------------------------------------------------------
// Room-state routing. \silent handled one-shot here (no ~silentNext).
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { tuneTime = TempoClock.beats },
		\piece,   { },
		\curtain, { /* fired internally by the conductor when the score routine finishes */ },
		\silent,  { synth.set(\amp, 0); }
	);
};



//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [yellow, magenta, cyan]

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.5;
	// [m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs, m.accelMassFiltered];
	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].abs;
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];

	// [m.accelMassFiltered * 3, m.rrateMassFiltered * 10, (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];

	// Gyro
		// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//roll
		[d.sensors.gyroEvent.y / pi.half]
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right
	// [(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	// [[(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2, (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2].sum] / 3;

	
	// [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];

	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
	// [d.port,d.sensors.digiInEvent].postln;
	// [d.sensors.digiInEvent[0],m.gyroXFiltered, m.gyroYFiltered];
	
};
