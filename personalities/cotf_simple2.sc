var m = ~model;
var bl=false;
var lastTime=0;
var barAnchor=nil;
// Subdivisions to shift the fire pattern later than the \D marker. Use this
// to correct for \D not landing on the piece's true downbeat: e.g. if the
// real downbeat is 2 subdivisions after \D, set beatOffset = 2. Range is
// 0..~scoreEventsPerBeat-1 (values beyond that just wrap).
var beatOffset = 0;
// How often to fire, in ~beatClock ticks (one tick == one beats.txt event ==
// one 1/16 in _mul4). 4=quarter, 2=eighth, 1=sixteenth, 8=half, 16=whole.
var subdivTicks = 2;
// Gate release fires this many notes-of-that-rate after the note starts, so
// higher values = more overlap between successive notes.
var sustainNotes = 2;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.7;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = SinOsc.ar(freq,0,1)!2;
    Out.ar(out, sig * env * amp);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	topEnvironment.use{

		
		// Pdef(m.ptn,
		// 	Pbind(
		// 	\instrument, \simple,
		// 	\octave, 0,
		// 	\dur, 1,
		// 	\root,       Pfunc { ~scoreVoicePool.choose.asInteger},
		// 	\note, 0,
		// 	\attack,0.03,
		// 	\decay, 0.1,
		// 	\sustain,0.1,
		// 	\release,1.04,
		// 	\args, #[],
		// 	)
		// );
		// Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);

	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
//   Pdef(m.ptn).remove;

};

//------------------------------------------------------------
~next = {|d|

	topEnvironment.use{

		// Fire \simple once per true musical beat, aligned to bar.
		//
		// ~beatClock.beats is anchored to the beats.txt event index (set by
		// OSCdef \beatSync). Dividing that by ~scoreEventsPerBeat only lands
		// on true beats if the anchor idx happens to be a downbeat — usually
		// it isn't, so we hear offbeats. Ground truth is ~labels: \D marks a
		// downbeat, \b marks the other subdivisions. We latch barAnchor to
		// the most recent \D and count true beats as (idx - barAnchor)
		// multiples of ~scoreEventsPerBeat.
		//
		// Gate release is scheduled ~sustainBeats true beats after fire —
		// longer than the fire interval, so notes overlap and the sound
		// moves in slower, ringing subdivisions rather than staccato hits.
		block {
			var idx = ~beatClock.beats.floor.asInteger;
			if((idx != lastTime) and: { ~labels.notNil } and: { idx.inclusivelyBetween(0, ~labels.size - 1) }, {
				var beatInBar;
				lastTime = idx;
				// Bootstrap: scan back to the last \D so we're aligned even
				// if the file loads mid-bar.
				if(barAnchor.isNil, {
					barAnchor = idx;
					while({ (barAnchor > 0) and: { ~labels[barAnchor] != \D } },
						{ barAnchor = barAnchor - 1 });
				});
				// Re-latch on every downbeat so bar-length drift can't
				// accumulate.
				if(~labels[idx] == \D, { barAnchor = idx });
				beatInBar = idx - barAnchor - beatOffset;
				if((beatInBar % subdivTicks) == 0, {
					var syn;
					// s.bind wraps the /s_new in a bundle sent with s.latency,
					// matching the audio DiskIn synth (which was also started
					// inside s.bind in ~seek). Without this the \simple synth
					// plays s.latency ahead of the music.
					s.bind {
						syn = Synth(\simple, [
							\amp,     0.4,
							\freq,    ~scoreVoicePool.choose.asInteger.midicps,
							\attack,  0.004,
							\decay,   0.3,
							\sustain, 0.0,
							\release, 0.1,
						]);
					};
					~beatClock.sched(sustainNotes * subdivTicks, {
						s.bind { syn.release }; nil
					});
				});
			});
		}
	};

};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, cyan , magenta]??

	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// [m.accelMass, m.accelMassFiltered];
	// [d.sensors.accelEvent.x.abs * d.sensors.accelEvent.y.abs *  m.accelMassFiltered] * 0.5;

	// Rotation
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [[d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z].sumabs];
	// [m.rrateMass, m.rrateMassFiltered];

	// Gyro
	// [(d.sensors.gyroEvent.x / pi)];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi)];//left right
	[(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
