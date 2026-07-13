var m = ~model;
var bl=false;
var synth;
var lastTime=0;
var barAnchor=nil;
// Subdivisions to shift the fire pattern later than the \D marker (\D isn't
// always the piece's true downbeat). Range 0..~scoreEventsPerBeat-1.
var beatOffset = 0;
// How often to change pitch, in ~beatClock ticks (one tick == one 1/16 in
// _mul4). 4=quarter, 2=eighth, 1=sixteenth, 8=half, 16=whole.
var subdivTicks = 4;
// Wrap ~scoreVoicePool pitches into a single octave starting here (MIDI).
// 60 = C4. Change to move the wrapped octave up or down.
var baseMidi = 60-24;

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = Saw.ar(freq,0.2,0.1) + SinOsc.ar(freq/2,0,1)!2;
    Out.ar(out, sig * env * amp.lagud(0.01, 1.1));
}).add;

//------------------------------------------------------------
// One long-lived \simple runs from ~init to ~deinit. Amp is driven every
// ~next tick (IMU rate) from accelMassFiltered; freq changes only on
// bar-aligned subdivision beats (see the block in ~next), so the pitch
// tracks the music while amp responds continuously to gesture.
~init = ~init <> {
	topEnvironment.use{
		synth = Synth(\simple, [
			\freq,    ((~scoreVoicePool.choose.asInteger % 12) + baseMidi).midicps,
			\amp,     0,
			\attack,  0.5,
			\decay,   0.1,
			\sustain, 1.0,
			\release, 1.0,
		]);
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|

	// Amp updates every ~next tick regardless of beat — continuous IMU
	// response. .set skips s.bind here on purpose: we want amp to feel
	// live, not audio-latency-delayed like the pitch changes below.
	var amp = m.accelMassFiltered.lincurve(0, 3.0, -90, -2,-1);
	synth.set(\amp, amp.dbamp);

	topEnvironment.use{
		// Bar-aligned subdivision detection (lifted from cotf_simple2). On
		// each qualifying beat, push a new freq into the running synth
		// via s.bind so the pitch change lands on the same s.latency
		// timeline as the audio.
		block {
			var idx = ~beatClock.beats.floor.asInteger;
			if((idx != lastTime) and: { ~labels.notNil } and: { idx.inclusivelyBetween(0, ~labels.size - 1) }, {
				var beatInBar;
				lastTime = idx;
				if(barAnchor.isNil, {
					barAnchor = idx;
					while({ (barAnchor > 0) and: { ~labels[barAnchor] != \D } },
						{ barAnchor = barAnchor - 1 });
				});
				if(~labels[idx] == \D, { barAnchor = idx });
				beatInBar = idx - barAnchor - beatOffset;
				if((beatInBar % subdivTicks) == 0, {
					s.bind {
						synth.set(\freq, ((~scoreVoicePool.first.asInteger % 12) + baseMidi).midicps);
					};
				});
			});
		}
	};
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass];
};
