var m = ~model;

// Mirrors of \droplet below. riseRatio is how far the bubble's pitch glides
// up as it collapses - the thing that makes a plink read as water rather than
// as a bell. The visual tightens its cusp by the same ratio, so the drop
// narrows exactly as the pitch rises.
var riseRatio = 2.6;
var dropDecay = 0.22;

m.accelMassFilteredAttack = 0.8;
m.accelMassFilteredDecay = 0.25;
m.gyroFilteredAttack = 0.6;
m.gyroFilteredDecay = 0.5;

//------------------------------------------------------------
SynthDef(\droplet, {
	|out = 0, freq = 900, amp = 0.2, pan = 0,
	 rise = 2.6, decay = 0.22, splash = 0.15, ring = 0.4|

	var fenv = EnvGen.ar(Env([1, rise], [decay * 0.8], \exp));
	var aenv = EnvGen.ar(Env.perc(0.001, decay, curve: -7), doneAction: Done.freeSelf);
	var body = SinOsc.ar(freq * fenv, 0, 0.7);
	var tail = SinOsc.ar(freq * fenv * 2.02, 0, 0.12 * ring);
	var tick = HPF.ar(WhiteNoise.ar, 4000)
		* EnvGen.ar(Env.perc(0.0005, 0.012)) * splash;
	var sig = (body + tail + tick) * aenv * amp;

	Out.ar(out, Pan2.ar(sig, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> {

	//--------------------------------------------------------
	// visual : one drop per plink, drawn as the teardrop curve
	//
	//     x = cos t
	//     y = sin t * |sin(t/2)|^sharp
	//
	// sharp = 0 is a mathematically exact circle; by sharp = 2 the tangent at
	// +x has collapsed to a true cusp and the body has narrowed to 0.65 of its
	// height. So one exponent carries the whole range the brief asks for, and
	// the point always lies on +x - which means \rotation aims the drop and
	// this function never computes a position.
	//
	// The wobble is metal1's \morphLine idea at a much smaller amplitude: a
	// few pixels riding the radius, so a near-circle breathes instead of
	// sitting dead on screen.
	//
	// Lineage: G5 calligraphic/gestural - "the mark records the movement of a
	// hand; speed and pressure are the information" - drawn as a G7 traced
	// contour that morphs. Atlas grammars G5 / G7, traversal 6
	// (event-triggered).
	//
	//   gyro.x    -> how sharp the drop is        (sharpMin..sharpMax, live)
	//   normTime  -> cusp tightens as it falls    (mirrors riseRatio)
	//   pitch     -> turquoise .. slate .. ocean  (\startColor)
	//   amp       -> how big the drop is          (\startSize)
	//   decay     -> how long it lives            (\duration)
	//
	// The range lives on the event and the model picks the point inside it,
	// so nothing is relayed through a file-level var and the shape responds
	// at frame rate rather than at note rate.
	~vdef.(\drop, { |ev, c|
		var mod = ev[\modulation] ? ();
		var n = ev[\numPoints] ? 72;
		var sharpMin = mod[\sharpMin] ? 0;
		var sharpMax = mod[\sharpMax] ? 1.1;
		var wob = mod[\wob] ? 1.8;
		var wobFreq = mod[\wobFreq] ? 5;
		var wobHarm = mod[\wobHarm] ? 3;
		var phase = mod[\phase] ? 0;
		var glide = mod[\glide] ? 0.35;
		var sz = c[\size];
		var pos = c[\pos];
		var t = c[\normTime];
		var gyro = m.gyroXFiltered.clip(-1, 1);
		var sharp = gyro.linlin(-1, 1, sharpMin, sharpMax)
			* (1 + (t * glide * (riseRatio - 1)));

		if(mod[\rest] == true, { nil }, {
			Array.fill(n, { |i|
				var a = i / n * 2pi;
				var taper = if(sharp > 0.00, { sin(a * 0.5).abs.pow(sharp) }, { 1 });
				var r = sz + (cos((a * wobHarm) + (2pi * wobFreq * c[\now]) + phase) * wob);
				pos + ((cos(a) * r) @ (sin(a) * r * taper))
			})
		})
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \droplet,
			\note, Prand([0, 3, 5, 7, 10, 12, 15], inf),
			\octave, Pwhite(5, 7),
			\root, Pseq([0, 0, -3, 2].stutter(24), inf),
			\dur, Pwrand([0.18, 0.3, 0.55, Rest(0.4)], [0.34, 0.28, 0.18, 0.2], inf),
			\rise, Pwhite(1.9, 3.4),
			\decay, Pwhite(0.14, 0.34),
			\splash, Pwhite(0.05, 0.3),
			\ring, Pwhite(0.1, 0.8),
			\pan, Pwhite(-0.7, 0.7),
			\func, Pfunc({ |e| ~onEvent.(e) }),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \drop,
			\numPoints, 72,
			\fill, false,
			\closed, true,

			\sx, Pwhite(-0.1, 0.1),
			\sy, Pwhite(-0.6, -0.5),
			\ex, Pkey(\sx),
			\ey, Pfunc({ |e| (e[\sy] ? 0) + 4 }),
			\yEnv, Pfunc({ Env([0, 1], [1], 2) }),

			\startSize, Pfunc({ |e| (e[\amp] ? 0.2).linlin(0.05, 0.4, 6, 34) }),
			\endSize, Pkey(\startSize),

			\startWidth, 3.2,
			\endWidth, 0.5,

			\startColor, Pfunc({ |e|
				var u = ((e[\note] ? 0) + ((e[\octave] ? 6) * 12)).linlin(60, 100, 1.0, 0.0);
				Color.hsv(
					0.47.blend(0.60, u),
					0.85.blend(0.95, u) - (0.6 * sin(u * pi)),
					0.95.blend(0.70, u),
					0.9
				)
			}),
			\endColor, Pfunc({ |e|
				var u = ((e[\note] ? 0) + ((e[\octave] ? 6) * 12)).linlin(60, 100, 1.0, 0.0);
				Color.hsv(0.47.blend(0.60, u), 0.9, 0.8, 0.0)
			}),
			\colorEnv, Pfunc({ Env([0, 1], [1], -3) }),

			\rotation, Pwhite(-0.35, 0.35) - 0.5pi,
			\duration, Pfunc({ |e| ((e[\decay] ? 0.22) * 4).clip(1.4, 2.6) }),

			\modulation, Pfunc({ |e| (
				rest: e.isRest,
				sharpMin: 0,
				sharpMax: 0.1,
				wob: rrand(1.2, 1.4),
				wobFreq: rrand(3.0, 7.0) * 0.4,
				wobHarm: [2, 3, 4].choose,
				phase: 2pi.rand,
				glide: 0.35,
				amp: 0
			) })
		)
	);

	Pdef(m.ptn).play(quant: 0.1);
	Pdef(m.ptn).pause;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
~onEvent = { |e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};

//------------------------------------------------------------
~next = { |d|

	var move = m.accelMassFiltered.linlin(0, 2, 0, 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.0, 0.05, 0.4, -2);
	var oct = m.accelMassFiltered.linlin(0, 2.5, 7, 5).round;

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\octave, oct);

	if(move > 0.03, {
		if(Pdef(m.ptn).isPlaying.not, {
			Pdef(m.ptn).resume(quant: 0.2);
		});
	}, {
		if(Pdef(m.ptn).isPlaying, {
			Pdef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d, p|
	[m.gyroXFiltered, m.accelMassFiltered.linlin(0, 2, 0, 1)];
};
