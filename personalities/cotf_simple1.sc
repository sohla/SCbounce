var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var bl=false;

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.3;
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
	~scoreAnchorBeat = 0;
	topEnvironment.use{

		Pdef(m.ptn,
			Pbind(
				\instrument, \simple,
				\out, ob,
				\octave, 0,
				\dur, 1,
				\root, Pfunc { ~scoreVoicePool.choose.asInteger },
				\note, 0,
				\attack,0.03,
				\decay, 0.1,
				\sustain,0.1,
				\release,1.04,
				\args, #[],
			)
		);
		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);

	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
  Pdef(m.ptn).remove;

};

//------------------------------------------------------------
~next = {|d|

	topEnvironment.use{
		if(m.accelMass > 0.04,{
			if( Pdef(m.ptn).isPlaying.not,{
				Pdef(m.ptn).resume(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
				~onResync = { |idx|
					Pdef(m.ptn).stop;
					Pdef(m.ptn).play(~beatClock,
						quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
				};
			});
		},{
			if( Pdef(m.ptn).isPlaying,{
				Pdef(m.ptn).pause();
			});
		});
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
	//[(d.sensors.gyroEvent.x / pi), (d.sensors.gyroEvent.y / pi.half), (d.sensors.gyroEvent.z / pi)];

	[m.accelMass];
  // [m.gyroXFiltered, m.gyroYFiltered, m.gyroZFiltered];
	// [ ((m.gyroZFiltered.fold(-0.5,0.5) * 2)+1) + (m.gyroYFiltered + 1)] - 2 * 0.5 ;
	// [(d.sensors.gyroEvent.y / pi.half).lincurve(-1.0,1.0,-1.0,1.0,3)];
};
