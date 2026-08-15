var m = ~model;
var dur = 0.22/2;

// SHARED ACROSS THE QUARTET. trainBass2 writes m.com.root; this voice
// reads it, so a harmony change turns every hue in the ensemble together.
var rootHue = { (m.com.root ? 0).linlin(-2, 3, -0.06, 0.06) };

// the arpeggio is a 5 note loop, so the ring has 5 stations.
var loop = 5;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\funMelody, {
    |out=0, freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.3, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2, pan = 0.0|
    var osc1, osc2, osc3, env, filter, output;
    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    osc1 = Saw.ar(freq, 1.0);
    osc2 = Pulse.ar(freq * 0.99, 0.5, 0.5);
    osc3 = SinOsc.ar(freq * 1.01, 0, 1.0);
    output = Mix([osc1, osc2, osc3]) * env * amp;
    filter = RLPF.ar(output, filtFreq, filtRes).tanh;
		// filter = ([filter, DelayN.ar(filter, 0.5, 0.5)+filter] * 2).tanh;

    Out.ar(out, Pan2.ar(filter,pan));
}).add;


//------------------------------------------------------------
~init = ~init <> {

	// visual : the arpeggio as a ring of five stations, inside the bass
	// wheel and on the same hub, so the two lock concentrically — the
	// harmony turns slowly on the outside, the arpeggio spins five times
	// faster inside it. That difference of rate is the piece: you can see
	// the melody lapping the harmony. Each note is a small triangle at
	// its own station; they linger and fade, so the ring is drawn by its
	// own repetition rather than by an outline.
	//
	// Lineage: cyclic/radial notation, atlas grammar G8 — time as angle,
	// loops with no seam. Concentric rates are the orrery verb from the
	// same section. Palette from atlas §0.5, saturated primaries on black.
	//
	//   loop step  -> angle round the ring   (\vstep -> \rotation)
	//   octave     -> ring radius            (\startSize)
	//   filtFreq   -> triangle size          (\modulation head)
	//   amp        -> stroke weight
	//   m.com.root -> hue, shared with all four voices
	~vdef.(\arpMark, { |ev, c|
		var mod = ev[\modulation] ? ();
		var at  = c[\pos] + (c[\size] @ 0);
		var r   = mod[\head] ? 14;
		var n   = ev[\numPoints] ? 3;
		Array.fill(n, { |i| at + Polar(r, (i / n * 2pi) - 0.5pi).asPoint })
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \funMelody,
			\note, Pseq([12,14,10,7,0]-1, inf),
			\envAtk, Pwhite(0.002,0.04, inf),
			// \envDec, Pwhite(0.2, 0.1, inf),
			// \root, Pseq([0].stutter(16), inf),
			\envSus, 0.0,
			\pan, Pseq([-0.3,0.3], inf),
    		// \filtRes, 0.9,

			\type, \customVisualEvent,
			\shape, \arpMark,
			\numPoints, 3,
			\vstep, Pseries(0, 1, inf),
			\rotation, ((Pkey(\vstep) % loop) / loop * 2pi) - 0.5pi,
			\startSize, Pfunc({ |e| (e[\octave] ? 5).linlin(3, 6, 122, 62) }),
			\endSize, Pkey(\startSize),
			\startWidth, Pfunc({ |e| (e[\amp] ? 0.2).linlin(0, 0.8, 1.5, 5) }),
			\endWidth, 0.4,
			\startColor, Pfunc({ |e| Color.hsv((0.13 + rootHue.()).wrap(0, 1), 0.80, 1.0, 1.0) }),
			\endColor, Pfunc({ |e| Color.hsv((0.13 + rootHue.()).wrap(0, 1), 0.95, 0.45, 0.0) }),
			\duration, 0.7,
			\modulation, Pfunc({ |e|
				(amp: 0, head: (e[\filtFreq] ? 2000).explin(50, 14000, 8, 26))
			}),

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:dur);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;

};

//------------------------------------------------------------

~onEvent = {|e|
	Pdef(m.ptn).set(\root, m.com.root+5);
};

//------------------------------------------------------------
~next = {|d|

	var oct = m.gyroYFiltered.linlin(-1,1,6,3).floor;
  	var envRel = m.accelMassFiltered.lincurve(0,1,0.1,2.6,2);
	var envDec = m.accelMassFiltered.lincurve(0,1,0.05,0.2,-2);

	var amp = m.accelMassFiltered.lincurve(0,2,0.001,0.5,-2);
  	var oamp = m.gyroYFiltered.lincurve(-1.0,1.0,0.0,0.5,-2);
	var ff = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).linexp(-1,1,50,14000);
	var rf = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).linexp(-1,1,0.9,0.2);

	if(oamp<0.05,{oamp=0.0;});
	if(amp<0.05,{amp=0.0;});
	
	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\filtFreq, ff);
	Pdef(m.ptn).set(\filtRes, rf);
	Pdef(m.ptn).set(\amp, (amp*0.7) + oamp);
	Pdef(m.ptn).set(\octave,oct);
	Pdef(m.ptn).set(\envRel,envRel);
	Pdef(m.ptn).set(\envDec,envDec);

	// if((amp + oamp) > 0.02,{
	// 	if( Pdef(~model.ptn).isPlaying.not,{
	// 		Pdef(~model.ptn).resume(quant:dur);
	// 	});
	// },{
	// 	if( Pdef(~model.ptn).isPlaying,{
	// 		Pdef(~model.ptn).pause();
	// 	});
	// });

};

~nextMidiOut = {|d|
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	// [m.accelMass * 0.1, m.accelMassFiltered * 0.1];
			// [((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2) ];
	[m.gyroYFiltered.lincurve(-1.0,1.0,0.0,1.0,-2)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

