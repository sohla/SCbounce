var m = ~model;
var group;
var lastTime = 0;

var partials    = [1, 2.76, 5.4];
var partialAmps = [1, 0.5, 0.25];

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay  = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay  = 0.3;
m.gyroFilteredAttack      = 0.7;
m.gyroFilteredDecay       = 0.7;

//------------------------------------------------------------
SynthDef(\templateAk, { |out = 0, freq = 300, amp = 0.2, decay = 1.2, pan = 0|
	var exciter = Impulse.ar(0) * PinkNoise.ar(1);
	var sig = DynKlank.ar(`[
		[1, 2.76, 5.4] * freq,
		[1, 0.5, 0.25],
		[1, 0.7, 0.4] * decay
	], exciter);
	sig = sig * amp;
	DetectSilence.ar(sig, doneAction: Done.freeSelf);
	Out.ar(out, Pan2.ar(sig, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> { |d|
	group = Group.new;

	~vdef.(\templateMark, { |ev, c|
		var n = ev[\numPoints] ? 48;
		var mod = ev[\modulation] ? ();
		var lobes = mod[\lobes] ? 3;
		var depth = mod[\depth] ? 0.25;
		var sz = c[\size];
		var pos = c[\pos];
		var t = c[\normTime];
		Array.fill(n, { |i|
			var a = i / n * 2pi;
			var r = sz * (1 + (depth * sin((a * lobes) + (t * 2pi))));
			pos + Polar(r, a).asPoint
		})
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \templateAk,
			\group, group,
			\note, Pseq([0, 4, 7, 11], inf),
			\octave, 5,
			\decay, 1.2,
			\pan, Pwhite(-0.4, 0.4),
			\func, Pfunc({ |e| ~onEvent.(e) }),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \templateMark,
			\numPoints, 48,
			\fill, false,
			\sx, Pwhite(-0.5, 0.5),
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e|
				((e[\note] ? 0) + ((e[\octave] ? 5) * 12)).linlin(48, 84, 0.7, -0.7)
			}),
			\ey, Pkey(\sy),
			\startSize, Pfunc({ |e| (e[\amp] ? 0.2).linlin(0.02, 0.5, 20, 90) }),
			\endSize, Pkey(\startSize),
			\startWidth, 4,
			\endWidth, 0.5,
			\startColor, Color.new(0.486, 1.0, 0.627, 0.9),
			\endColor, Color.new(0.486, 1.0, 0.627, 0.0),
			\rotation, 0,
			\duration, 1.4,
			\modulation, Pfunc({ |e| (
				rest: e.isRest,
				lobes: 3,
				depth: 0.25,
			) }),
		);
	);

	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\dur, 0.4);

	Pdef(m.ptn).play(quant: 0.1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
	};
};

//------------------------------------------------------------
~onEvent = { |e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};

//------------------------------------------------------------
~next = { |d|
	var amp = m.accelMassFiltered.lincurve(0, 1.4, -50, -5, -1);
	var tilt = (d.sensors.gyroEvent.y / pi.half);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p|
	[m.accelMass, m.accelMassFiltered];
};
