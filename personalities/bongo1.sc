var m = ~model;
var synth;

// Mirrors the DynKlank in \bongo1 below : four stretched membrane modes,
// their relative amplitudes, and their relative decay times.
var modeRatios = [1, 1.7, 2.3, 2.9];
var modeAmps   = [1, 0.6, 0.3, 0.2];
var modeDecays = [0.3, 0.2, 0.15, 0.1];
// position around the cycle ring. State, not a tunable - the cycle LENGTH
// is set on the event, next to everything else.
var step = 0;

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.99;


//------------------------------------------------------------
SynthDef(\bongo1, {
    arg out=0, freq=200, amp=0.5, pan=0,
    tension=0.7,  // affects brightness/pitch bend
    damp=0.3,    // affects decay time
    pos=0.3;     // affects harmonic content

    var exciter, membrane, env, earlyRefs, sound;

    // Exciter (strike impulse with noise)
    exciter = Impulse.ar(0) * PinkNoise.ar(1);

    // Membrane simulation using resonant filters
    membrane = DynKlank.ar(
        `[
            // Frequencies are harmonically related but stretched
            [freq, freq*1.7, freq*2.3, freq*2.9],
            // Amplitudes decrease for higher modes
            [1, 0.6, 0.3, 0.2] * pos.squared,
            // Decay times shorter for higher modes
            [damp*0.3, damp*0.2, damp*0.15, damp*0.1]
        ],
        exciter
    );

    // Add initial pitch bend for attack
    membrane = membrane + (
        SinOsc.ar(freq * Line.kr(1.5, 1, 0.02))
        * EnvGen.kr(Env.perc(0.001, 0.09))
        * tension
    );

    // Basic envelope
    env = EnvGen.kr(Env.perc(0.001, damp ));

    // Body filter for overall tone
    sound = BPF.ar(
        membrane,
        freq * [1, 2.1],
        0.3
    ).sum;

    // Early reflections
    earlyRefs = DelayN.ar(
        sound,
        0.02,
        [0.01, 0.012, 0.013]
    ).sum * 0.3;


    // Room reverb
    sound = FreeVerb.ar(
        sound + earlyRefs,
        mix: 0.2,
        room: 0.5,
        damp: 0.4
    );

    sound = Pan2.ar(sound * env * amp, pan);
    DetectSilence.ar(sound, doneAction: Done.freeSelf);
    Out.ar(out, sound);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	//------------------------------------------------------------
	// visual : a cyclic score. Hits are laid round a ring by their position
	// in a 128 step cycle, which runs long against the 14 step \dur pattern
	// so the rhythm precesses round the ring rather than stamping the same
	// figure each lap. Rests draw nothing, so the gaps are real.
	//
	// Lineage: the cyclic notations in the atlas (gong-point rings, nested
	// cycles) crossed with Chladni/cymatic plate figures for the mark
	// itself. Atlas grammars G8 / G7.
	//
	//   cycle    -> angle round the ring       (\cyc -> \sx \sy)
	//   pitch    -> radius                     (\rad -> \sx \sy)
	//   amp      -> head size                  (\startSize)
	//   damp     -> how long the mark lives    (\duration)
	//   tension  -> attack overshoot           (\modulation)
	//
	// A draw func, because concentric rings are several subpaths. It knows
	// no geometry of its own : the event is anchored at the canvas centre
	// (\sx \sy default 0), \rotation carries the angle, and \startSize is
	// the ring radius in pixels. The draw func just steps out along +x.
	~vdef.(\membrane, { |ev, c|
		var mod = ev[\modulation] ? ();
		var rest = mod[\rest] ? false;
		var chirpAmt = mod[\chirp] ? 0.5;
		var chirpTime = mod[\chirpTime] ? 0.04;
		// how wide the arc reads, in radians. Deliberately NOT tied to the
		// number of cycle steps - that sets where marks land, not how long
		// a tick should be.
		var arcSpan = mod[\arcSpan] ? 0.45;
		var arcAlpha = mod[\arcAlpha] ? 0.22;
		var head = mod[\head] ? 30;
		var t = c[\normTime];
		var mid = c[\pos];		// sx/sy default 0 -> the canvas centre
		var ring = c[\size];	// startSize -> endSize, ring radius in PIXELS
		var wid = c[\width];	// startWidth -> endWidth
		var col = c[\color];	// startColor -> endColor
		var tension = (mod[\tension] ? 0.5).clip(0, 1);
		// the synth's Line.kr(1.5, 1, 0.02) attack bend, scaled by tension
		var chirp = 1 + (chirpAmt * tension * exp(t.neg / chirpTime));
		// \rotation has already swung the frame to this step's angle, so the
		// mark is simply "out along +x by the ring radius". Radius comes from
		// the event's size, which is in pixels and therefore round on any
		// canvas - no bounds, no aspect term.
		var pos = mid + (ring @ 0);

		// Rests reach here as events but draw nothing - the silence is the
		// gap, and it is the most important thing on screen.
		if(rest.not, {

			// this step's slice of the cycle, so the ring is assembled only
			// from steps that actually sounded
			Pen.width = wid;
			Pen.strokeColor = Color.new(col.red, col.green, col.blue,
				col.alpha * arcAlpha);
			// centred on +x too, so \rotation carries it round with the mark
			Pen.addArc(mid, ring, arcSpan.neg * 0.5, arcSpan);
			Pen.stroke;

			// The membrane : higher modes are tighter nodal circles and die
			// soonest, so the bullseye thins from the inside out.
			//
			// modeDecays is used directly as a normTime constant rather than
			// being scaled by damp. In the synth the mode decay is
			// damp*modeDecays SECONDS against an Env.perc(0.001, damp), so
			// once \duration tracks damp the two cancel and the decay SHAPE
			// is the same every hit - damp shows up as how long the mark
			// lives, which is what it actually does to the drum.
			modeRatios.do { |ratio, i|
				var a = modeAmps[i] * exp(t.neg / modeDecays[i]);
				var r = head / ratio * chirp;
				// every dimension is the event's own - this func only scales
				// them by the mode's current amplitude
				if(a > 0.01, {
					Pen.width = wid * a;
					Pen.strokeColor = Color.new(col.red, col.green, col.blue,
						col.alpha * a);
					Pen.addOval(Rect.aboutPoint(pos, r, r));
					Pen.stroke;
				});
			};
		});
	});

	Pdef(m.ptn,
		Pbind(
		    \instrument, \bongo1,
			\dur, Pseq([Pseq([Rest(0.25), 0.25], 5) ,0.6666,0.6666,0.666, 0.25]* 0.5 , inf),
			\octave, Pseq([2,3,4] + 2, inf),
		    \note, Pseq([0,5,9,2,7,4,3], inf),
			// \amp, Pseq([0.9, 0.6, 0.8], inf),
		    \tension, Pwhite(0.001, 1),
			// \damp, 4,//Pwhite(0.5,2),
            // \dur, 0.125 * 0.5,
		    \pos, 0.4,//Pwhite(0.1, 0.9),
		    \pan, Pwhite(-0.3, 0.3),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],

			\type, \customVisualEvent,
			\shape, \membrane,

			// ---- the polar composition ----
			// Where on the ring, and how far out. The draw func turns these
			// into canvas coordinates; see the note in \membrane for why the
			// position keys are not used for a canvas-anchored ring.
			//
			// \cyc advances once per event, rests included, so the step
			// counter is read and written in exactly one place.
			\cyc, Pfunc({
				var s = 128;
				var ph = (step % s) / s;
				step = step + 1;
				ph
			}),
			// radius = pitch, high notes further out
			\rad, Pfunc({ |e|
				((e[\note] ? 0) + ((e[\octave] ? 5) * 12)).linlin(48, 81, 0.34, 0.78)
			}),
			// angle round the ring. The core rotates about the mark's own
			// origin, which for a centred event IS the ring centre - so the
			// draw func never computes an angle at all.
			\rotation, Pfunc({ |e| (e[\cyc] * 2pi) - 0.5pi }),
			// ring radius, in pixels. Pixels are pixels, so this is round on
			// the panel, the HDMI window and a grid column alike.
			\startSize, Pfunc({ |e| e[\rad] * 400 }),
			\endSize, Pkey(\startSize),
			// stroke weight = dynamic. The draw func multiplies this by each
			// mode's current amplitude, so the bullseye is heaviest at the
			// strike and thins as the head settles.
			\startWidth, 2,
			\endWidth, 0.8,
			// saturated primary on black
			\startColor, Color.new(1.0, 0.42, 0.12, 0.95),
			\endColor, Color.new(1.0, 0.42, 0.12, 0.0),
			// The mark lasts as long as the drum rings. explin, not linexp:
			// ~next drives damp through lincurve(...,5), so it spends most
			// of its life near 0.001 and a linear reading of it would leave
			// every hit an identical one-frame flash.
			\duration, Pfunc({ |e|
				(e[\damp] ? 1).clip(0.001, 14).explin(0.001, 14, 0.3, 3.2)
			}),
			// Only what is NOT a standard event key. phase and radius are
			// \cyc and \rad passed straight through - one source, no second
			// copy of the geometry.
			\modulation, Pfunc({ |e| (
				rest: e.isRest,
				tension: e[\tension] ? 0.5,
				chirp: 0.5,
				chirpTime: 0.04,
				// arc width in radians - 0.45 is about 26 degrees, roughly a
				// tenth of the ring. Tune the tick length here.
				arcSpan: 0.45,
				arcAlpha: 0.22,
				// head radius in px, from amp - ~next drives it 0.02..3.
				// The event's own size is the RING radius, so the membrane
				// travels here.
				head: (e[\amp] ? 1).clip(0.02, 3).linexp(0.02, 3, 12, 70)
			) })
		);
	);

	Pdef(m.ptn).play(quant:0.125);
    Pdef(m.ptn).pause;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
~onEvent = {|e|
	if(e.root != m.com.root,{
		// "key change".postln;
		Pdef(m.ptn).reset;
	});
	Pdef(m.ptn).set(\root, m.com.root);
};

//------------------------------------------------------------
~next = {|d|

	var amp = m.accelMassFiltered.lincurve(0,1.0,0.02,3,3);
	var damp = m.accelMassFiltered.lincurve(0,1.0,0.001,14,5);
	// tells the visual router which device these shapes came from
	Pdef(m.ptn).set(\viewID, d.port);

	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\damp, damp);

	if(m.accelMassFiltered > 0.004,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.125);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};




