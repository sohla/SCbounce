var m = ~model;
var synth;
var bsynth;
var note = 48 + 4;
var lastTime = 0;

// The device, captured lexically in ~init. It CANNOT be read as ~device
// from inside the vdef: a draw func runs in drawCanvas's context, not the
// personality Environment, so ~device there resolves against the wrong
// currentEnvironment and comes back nil. m works only because it is a
// lexical var too. This is a reference, not a copied value, so
// dev.sensors is live at frame rate.
var dev;

// SHARED ACROSS THE QUARTET. trainBass2 writes m.com.root; this voice
// reads it, so a harmony change turns every hue in the ensemble together.
var rootHue = { (m.com.root ? 0).linlin(-2, 3, -0.06, 0.06) };


m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.8;


// add another trigger that adds impulse to the synth, with offset freq's

SynthDef(\warmPadMove2, {
	|out=0, gate=1, freq=440, amp=0.1,atk=0.03, dec=0.2, sus=0.8, rel=1.0,filtMin=500, filtMax=5000, filtSpeed=0.5,
	detuneAmount = 1.001,chorusRate=0.5, chorusDepth=0.01,pan=0, spread=0.2, lfoFreq=1, excAttack=0.01, excRelease=0.1, trig=1|

    var sig, env, filt, chorus, numVoices=8, sub;
		var pulse = LFCub.ar(lfoFreq,pi,0.5,0.5);
		var exciter = EnvGen.kr(Env.perc(excAttack, excRelease), trig)+1;
		freq = freq.lag(3);


    // Main envelope
    env = EnvGen.kr(
        Env.adsr(atk, dec, sus, rel),
        gate,
        doneAction: 2
    );

    // Multiple slightly detuned oscillators for warmth
    sig = Array.fill(numVoices, { |i|
        var detune = i * detuneAmount;
        var oscillator = SinOsc.ar(freq * (1 + detune)) +
                        Saw.ar(freq * (1 + detune), pi/2 * i) * 0.3;
        Pan2.ar(oscillator, pan + detune)
    }).sum;


    // Chorus effect
    // Anti-aliased chorus using all-pass filter
    chorus = Array.fill(2, {
        var maxDelay = 0.05;
        var delayTime = SinOsc.kr(
            chorusRate + rand(0.1),
            rrand(0, 2pi)
        ).range(0, chorusDepth);

        AllpassC.ar(
            sig,
            maxDelay,
            delayTime + (chorusDepth * 0.1),
            0.1  // Shorter decay time for cleaner sound
        )
    });    // Final processing
	sub = LFTri.ar(freq * (3/2), pi, 13).tanh * 0.2;
	sig = Mix([sig, chorus.sum, sub]) / (numVoices + 3);

	    // Filter sweep
    filt = SinOsc.kr(filtSpeed.lag(1.5)).range(filtMin, filtMax);
    sig = RLPF.ar(sig, filt.lag(0.3), 0.5);

  // sig = sig * env * amp;

    // Output with stereo spread
    sig = Splay.ar(sig, spread);
		sig = GVerb.ar(sig.tanh * 0.2,4,0.1);
	Out.ar(out, sig * Amplitude.kr(amp,0.1,0.8) * env * exciter);
}).add;


//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {|d|

	// visual : the tunnel itself.
	//
	// This voice is one long-lived pad, not a pattern, so it is the only
	// thing here that does not travel. That is the right job for a drone:
	// the other three are traffic, this is the structure they move
	// through. It draws the perspective lines running from the vanishing
	// point out to the edges — the walls, the thing that tells you there
	// IS a tunnel and where its centre is. Without it the other voices
	// are just marks flying outward; with it they are inside something.
	//
	// ONE held event, fired here, never re-laid. duration: inf means the
	// cull never expires it, so there is no pulse of its own to beat
	// against the music — the walls simply exist. It also means normTime
	// stays pinned at 0, so the core's start->end blending never advances:
	// every end* and *Env key would be inert, and they are left off rather
	// than written and quietly ignored. What you set below is what you
	// see, which is what makes it adjustable in one place.
	//
	// All the motion therefore has to come from this draw func reading
	// live state each frame, which is the metal1 \sheetFrame idiom.
	// Nothing is relayed: lfoFreq is recomputed from m.gyroYFiltered here,
	// so tilting sets how fast the shimmer travels round the tunnel. Every
	// other tunable is a ? fallback on \modulation, so the single event is
	// the one place to adjust the whole thing.
	//
	// clearEvents in personalityController removes it on unload — without
	// that a held event would outlive its vdef and degrade into a stray
	// fallback circle.
	//
	// Lineage: circuit / apparatus, atlas grammar G12 — the score as a
	// diagram of the space rather than of the sounds — crossed with the
	// radial G8 the other three fly along. Palette from atlas §0.5,
	// saturated primaries on black; this voice takes the coolest, darkest
	// end so it reads as architecture, never as an event.
	//
	//   gyro x/y/z -> the stick's orientation, same angles as threeDeeView
	//   accel      -> how far the whiskers reach out
	//   \modulation -> box proportions, camera, segment count, reach, alphas
	dev = d;

	// The airstick itself, wireframed and projected by hand.
	//
	// This is threeDeeView's cube, rebuilt so it can live on the visual
	// canvas: Canvas3D is a View class and cannot be used inside a draw
	// func, so the rotation and the perspective divide are done here. The
	// three Euler angles are lifted verbatim from threeDeeView so the
	// stick on the canvas reads at the same attitude as the one on the
	// device panel — same wraps, same negations, same X then Y then Z
	// order. Body proportions are its mScale(1.0, 0.4, 0.5), and the
	// camera is its distance 3.5 / perspective 0.75.
	//
	// dev is read here, not ~device — see the note at the top of the file.
	//
	// The whiskers are the part to play with. Every vertex can shoot a
	// line on outward from the body along its own direction, length from
	// \reach scaled by accel, so at rest you get a clean wireframe stick
	// and on movement it throws lines out into the tunnel. Set reach to 0
	// on the event for the stick alone. They are drawn as segmented
	// polylines rather than 2-point spans so \modulation can bend them
	// later — a 2-point path is all endpoints and never moves.
	~vdef.(\airstick, { |ev, c|
		var mod   = ev[\modulation] ? ();
		var g     = dev.sensors.gyroEvent;
		var rx    = (g.x + pi.half).wrap(-pi, pi);
		var ry    = (g.y).wrap(-pi.half, pi.half).neg;
		var rz    = (g.z - pi.half).wrap(-pi, pi).neg;
		var bw    = mod[\bw] ? 1.0;
		var bh    = mod[\bh] ? 0.4;
		var bd    = mod[\bd] ? 0.5;
		var dist  = mod[\dist] ? 3.5;
		var persp = mod[\persp] ? 0.75;
		var seg   = (mod[\seg] ? 8).max(2);
		var edgeA = mod[\edgeAlpha] ? 1.0;
		var rayA  = mod[\rayAlpha] ? 0.45;
		var reach = (mod[\reach] ? 0.0)
			* m.accelMassFiltered.linlin(0, 2.0, 0, 1);
		var rot, proj, verts, edges;

		rot = { |v|
			var x = v[0], y = v[1], z = v[2], t;
			t = y; y = (t * cos(rx)) - (z * sin(rx)); z = (t * sin(rx)) + (z * cos(rx));
			t = x; x = (t * cos(ry)) + (z * sin(ry)); z = (t * sin(ry)).neg + (z * cos(ry));
			t = x; x = (t * cos(rz)) - (y * sin(rz)); y = (t * sin(rz)) + (y * cos(rz));
			[x, y, z]
		};
		proj = { |v|
			var k = persp / (dist - v[2]).max(0.05) * c[\size];
			c[\pos] + ((v[0] * k) @ (v[1] * k))
		};

		verts = [
			[-1,-1,-1], [1,-1,-1], [1,1,-1], [-1,1,-1],
			[-1,-1, 1], [1,-1, 1], [1,1, 1], [-1,1, 1]
		].collect({ |v| rot.([v[0] * bw, v[1] * bh, v[2] * bd]) });

		edges = [ [0,1],[1,2],[2,3],[3,0], [4,5],[5,6],[6,7],[7,4],
		          [0,4],[1,5],[2,6],[3,7] ];

		edges.do({ |e|
			var p0 = verts[e[0]];
			var p1 = verts[e[1]];
			c[\render].(
				Array.fill(seg, { |j| proj.(p0 + ((p1 - p0) * (j / (seg - 1)))) }),
				1, edgeA, false);
		});

		if(reach > 0.001, {
			verts.do({ |v|
				var far = v * (1 + reach);
				c[\render].(
					Array.fill(seg, { |j| proj.(v + ((far - v) * (j / (seg - 1)))) }),
					0.6, rayA, false);
			});
		});
		nil
	});

	// THE single event. Adjust the stick here and nowhere else.
	(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
		shape: \airstick,
		sx: -0.5, sy: 0, ex: 0, ey: 0,
		startSize: 500,
		startWidth: 2.6,
		startColor: Color.hsv(0.82, 0.90, 0.55, 1.0),
		closed: false,
		duration: inf,
		modulation: (
			amp: 0,
			bw: 1.0, bh: 0.4, bd: 0.5,
			dist: 3.5, persp: 0.75,
			seg: 8,
			reach: 2.5,
			edgeAlpha: 1.0,
			rayAlpha: 0.45
		)
	).play;

	synth = Synth(\warmPadMove2, [
		\freq, note.midicps, 
		\amp, 0,
		\gate, 1,
    \atk, 1.02,
    \rel, 1.8,
    // \filtMin, 800,
    // \filtMax, 8000,
    \filtSpeed, 0.1,
    \chorusRate, 0.001,
    \chorusDepth, 0.0001,
		\detuneAmount, 0.0004
	]);
};


//------------------------------------------------------------
// triggers
//------------------------------------------------------------
~deinit = ~deinit <> {
	// Pdef(m.ptn).remove;
	// synth.free;
    synth.set(\gate, 0);
    bsynth.set(\gate, 0);

	synth.free;
	bsynth.free;

};

// example feeding the community
~onEvent = {|e|
	if(e.root != m.com.root,{
		// "key change".postln;
		synth.set(\freq, (note + e.root).midicps);
	});
	m.com.root = e.root;
	m.com.dur = e.dur;
};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = 0.5 * 2.pow(m.accelMassFiltered.linlin(0,3,0,2).floor).reciprocal;
	var a = m.accelMassFiltered.lincurve(0,1.5,0,1,-6);
	var filtSpeed = m.accelMassFiltered.lincurve(0,2.5,0.1,40,3);
	var lfoFreq = m.accelMassFiltered.lincurve(0,2.5,0.1,8,-1);
	var fmin = (m.gyroZFiltered.fold(-0.5,0.5) * 2).linexp(-1.0,1.0,200,500);
	var fmax = (m.gyroZFiltered.fold(-0.5,0.5) * 2).linexp(-1.0,1.0,200,8000);
	var notes = [-12,-5,-2,0,7,12];
	var idx = m.gyroYFiltered.lincurve(-0.4,0.4,0.0,notes.size-1,-2).asInteger;
	var hue = (note + m.com.root+ notes[idx]).linlin(40, 66, 0, 1);

	if(a<0.03,{a=0});
	if(a>0.9,{a=0.9});
    
    synth.set(\freq, (note + m.com.root).midicps);
	synth.set(\amp, a * 0.5);
	synth.set(\filtSpeed, filtSpeed);
	synth.set(\lfoFreq, lfoFreq);
	synth.set(\filtMin, fmin);
	synth.set(\filtMax, fmax);


	if(d.sensors.accelEvent.x > 1.1, {
	// if(m.accelMassFiltered > 1.0, {

		if(TempoClock.beats > (lastTime + 0.055),{
			// same expression the bsynth below takes for its \amp, so the
			// ring is the strength of this hit rather than a guess at it.
			var hitAmp = m.accelMassFiltered.lincurve(1,2.5,0.1,0.5,-2);
			var chorus = m.accelMassFiltered.lincurve(0,2.5,1.0,0.2,-1);

			lastTime = TempoClock.beats;

			bsynth = Synth(\warmPadMove2, [
				\freq, (note + m.com.root+ notes[idx]).midicps * 4, 
				\amp, m.accelMassFiltered.lincurve(1,2.5,0.1,0.2,-1),
				\gate, 1,
				\atk, m.accelMassFiltered.lincurve(1,2.5,0.2,0.03,1),
				\rel, m.accelMassFiltered.lincurve(1,2.5,3.2,1.03,1),
				\filtMin, 8000,
				\filtMax, 12000,
				\filtSpeed, 0.1 * chorus,
				\chorusRate, 0.1 * chorus,
				\chorusDepth, 0.01 * chorus,
				\detuneAmount, 0.0004 * chorus
			]);
			// mod 

			s.bind { bsynth.set(\gate, 0) };

			// visual : the hit. One bright ring at the vanishing point.
			//
			// It is the only mark in the quartet that starts big and bright
			// instead of creeping in from far away, and the only one whose
			// size curve is NEGATIVE (-3): it snaps open and then decelerates.
			// Everything else here accelerates toward you on a +3 because it
			// is approaching. A hit has no approach — it has already happened
			// — so it wants the opposite curve, and that difference is what
			// stops it reading as just another lamp coming down the tunnel.
			//
			// The ring thins as it opens, so it dissipates rather than
			// hanging as a hoop. Outline, never filled: a filled disc this
			// size would white out the whole tunnel on every hit.
			//
			//   accel -> ring size, stroke weight, and how bright it starts
			(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
				shape: \circle,
				numPoints: 48,
				sx: -0.5, sy: 0, ex: -0.5, ey: 0,
				startSize: hitAmp.linlin(0.1, 0.5, 40, 90) * 0.5,
				endSize: hitAmp.linlin(0.1, 0.5, 260, 480) * 0.5,
				sizeEnv: Env([0, 1], [1], -3),
				startWidth: hitAmp.linlin(0.1, 0.5, 4, 9),
				endWidth: 0.5,
				widthEnv: Env([0, 1], [1], -3),
				startColor: Color.hsv(hue, 0.85, 1.0, 0.95),
				endColor: Color.hsv(hue, 0.60, 1.0, 0.0),
				duration: 3.1
			).play;

		},{
		});
	});

};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	// [m.accelMass * 0.1, m.accelMassFiltered * 0.1];
	// [m.rrateMassFiltered];
	// [d.sensors.accelEvent.x.abs];
		// [m.gyroYFiltered.lincurve(-0.5,0.5,0.0,4.0,-2).asInteger / 4.0];
[(m.gyroZFiltered.fold(-0.5,0.5) * 2)];
	// [m.rrateMassFiltered, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

