var m = ~model;
var dur = 0.22/2;

// SHARED ACROSS THE QUARTET. trainBass2 writes m.com.root; this voice
// reads it, so a harmony change turns every hue in the ensemble together.
// This voice is held near-white, so the root only tints it.
var rootHue = { (m.com.root ? 0).linlin(-2, 3, -0.06, 0.06) };

// the note loop is 5 long, and that length is the additive cell.
var cell = 5;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.99;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\funMelody, {
    |out=0, freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2, pan = 0.0|
    var osc1, osc2, osc3, env, filter, output;
    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    osc1 = Saw.ar(freq, 1.5);
    osc2 = Pulse.ar(freq * 0.99, 0.5, 0.5);
    osc3 = SinOsc.ar(freq * 1.01, 0, 1.5);
    output = Mix([osc1, osc2, osc3]) * env * amp;
    filter = RLPF.ar(output, filtFreq, filtRes);
		// filter = ([filter, DelayN.ar(filter, 0.5, 0.5)+filter] * 2).tanh;

    Out.ar(out, Pan2.ar(filter,pan));
}).add;


SynthDef(\chooka, {
    |out=0, freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2, pan = 0.0|
    var osc1, osc2, osc3, env, filter, output;
    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    osc1 = WhiteNoise.ar(0.2);
    osc2 = BrownNoise.ar(0.2);
    osc3 = Pulse.ar(freq * 0.25, LFCub.ar(10,0,1,1), 0.5) * 0.8;
    // osc3 = SinOsc.ar(freq * 1.01, 0, 1.5);
    output = Mix([osc1, osc2]) * env * amp;
    filter = RLPF.ar(output, filtFreq, filtRes);
		// filter = ([filter, DelayN.ar(filter, 0.5, 0.5)+filter] * 2).tanh;

    Out.ar(out, Pan2.ar(filter,pan));
}).add;

//------------------------------------------------------------
// visual : the additive cell. Nothing here depicts steam — the pulse is
// drawn as process, not as weather.
//
// The note loop is 5 long, so each pass lays five hard-edged bars, all
// pivoting off one fixed left margin, each one unit longer than the last:
// 1 2 3 4 5, then back to 1. That is the figure Glass builds Einstein
// out of, and it was already in this file as Pseq([12,14,10,7,0]-1) —
// the bar length simply counts the position in that cell. The fifth bar
// fills solid, so the cycle closes with an accent and the traversal is
// legible (atlas §3.4) without a cursor.
//
// The strangeness is that the rank is not level and never settles.
// Every completed cell adds 0.045 rad, so the whole fan pivots about its
// margin at about one turn every 77 seconds — far too slow to watch,
// impossible to miss after a minute. Rigid geometry quietly off-axis.
// Rotation is derived from the step count, so it is exact and needs no
// stored state.
//
// Lineage: block / register-band, atlas grammar G3-G4 — flat rectangles,
// bandwidth as height, no modelling, no taper, hard edges. Wilson's light
// bars by way of the atlas's data / test-pattern palette (§0.5): white on
// black, one accent, no gradient, modulation amp pinned to 0 so nothing
// wobbles.
//
//   position in cell -> bar length          (\vstep -> \startSize)
//   note             -> which row it sits on (\sy)
//   filtFreq         -> bar thickness, i.e. the filter's band
//   cells elapsed    -> pivot angle          (\rotation)
//   last of cell     -> solid fill, the accent
//   silence          -> length 0, nothing drawn
~init = ~init <> {

	// a bar anchored at its LEFT end, so length grows rightward and
	// \rotation pivots the rank about a shared margin rather than
	// spinning each bar on its centre.
	~vdef.(\cell, { |ev, c|
		var mod   = ev[\modulation] ? ();
		var len   = c[\size] * 2;
		var thick = mod[\thick] ? 7;
		var p     = c[\pos];
		[ p + (0 @ thick.neg), p + (len @ thick.neg),
		  p + (len @ thick),   p + (0 @ thick) ]
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \chooka,
			\note, Pseq([12,14,10,7,0]-1, inf),
			// \octave,Pseq([5,6].stutter(2),inf),
			// \root, Pseq([0].stutter(32), inf),
			// \envAtk, Pwhite(0.02,0.04, inf),
			// \envDec, Pwhite(0.2, 0.1, inf),
			\envSus, 0.0,
			// \envRel,Pkey(\octave) * 0.4,
    		// \amp, Pkey(\octave).reciprocal * 0.13,
			\pan, Pseq([-0.3,0.3], inf),
    		\filtRes, 0.8,

			\type, \customVisualEvent,
			\shape, \cell,
			\vstep, Pseries(0, 1, inf),
			\sy, 0.02,
			\ey, -0.02,
			\sx, Pfunc({ |e| (e[\note] ? 0).linlin(-1, 13, 0.40, -0.40) }),
			\ex, Pkey(\sx),
			\rotation, (Pkey(\vstep) / cell).floor * 0.045,
			\startSize, Pfunc({ |e|
				if((e[\amp] ? 0) < 0.02, { 0 }, { (((e[\vstep] ? 0) % cell) + 1) * 22 })
			}),
			\endSize, Pkey(\startSize),
			\startWidth, Pfunc({ |e| (e[\amp]).linlin(0, 1, 0, 2.5) }),
			\endWidth, Pkey(\startWidth),
			\fill, Pfunc({ |e| ((e[\vstep] ? 0) % cell) == (cell - 1) }),
			\startColor, Pfunc({ |e|
				Color.hsv((0.55 + rootHue.()).wrap(0, 1), 0.06, 1.0,
					if(((e[\vstep] ? 0) % cell) == (cell - 1), { 0.90 }, { 0.50 }))
			}),
			\endColor, Pfunc({ |e| Color.hsv((0.55 + rootHue.()).wrap(0, 1), 0.06, 1.0, 0.0) }),
			\duration, Pkey(\envRel),
			\modulation, Pfunc({ |e|
				(amp: 0, thick: (e[\amp]).linlin(0, 1, 3.5, 15))
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

	var oct = m.gyroYFiltered.linlin(-1,1,8,4).floor;
  	var envRel = m.accelMassFiltered.lincurve(0,2.5,0.4,1.1,1);
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.001,1.0,-1);
	var ff = (d.sensors.gyroEvent.y / pi.half).lincurve(-1,1,80,14000,-2);
	var atk = m.accelMassFiltered.lincurve(0,2.5,0.02,0.001,-1);
	var dcy = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1,1,0.001,0.4,-2);
	var lff = (m.gyroZFiltered.fold(-0.5,0.5) * 2).linlin(-1,1,110.0,800);
	var hff = (m.gyroZFiltered.fold(-0.5,0.5) * 2).linexp(-1,1,250.0,8000);
	
	if(amp < 0.02) { amp = 0.0 };

	if(m.gyroYFiltered > -0.1, {
		if(m.gyroYFiltered < 0.1, {
				amp = rrand(0.2, 0.35);
				Pdef(m.ptn).set(\filtFreq, rrand(lff, hff));
				Pdef(m.ptn).set(\envAtk, rrand(0.04, 0.05));
				Pdef(m.ptn).set(\envRel,rrand(0.2,0.5));
				Pdef(m.ptn).set(\envDec, rrand(0.1,0.3));

		},{
			Pdef(m.ptn).set(\filtFreq, ff);
			Pdef(m.ptn).set(\envAtk,atk);
			Pdef(m.ptn).set(\envRel,envRel);
			Pdef(m.ptn).set(\envDec, dcy);
			
		}); 
	});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\octave,oct);

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
	// [m.gyroZFiltered.lincurve(-1.0,1.0,0.0,1.0,-2)];
	// [(m.gyroZFiltered/pi.half).linlin(-1.0,1.0,0.0,1.0)];
	[(m.gyroZFiltered.fold(-0.5,0.5) * 2)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

