var m = ~model;
var synth;
var bsynth;
var note = 48 + 4;
var lastTime = 0;

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
    sig = RLPF.ar(sig, filt, 0.5);

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
	//   gyro y     -> lfoFreq, the rate the shimmer travels round
	//   \modulation -> everything else: ray count, breath depth, wobble,
	//                  per-ray phase spread, alpha
	~vdef.(\tunnelWalls, { |ev, c|
		var mod       = ev[\modulation] ? ();
		var a         = m.accelMassFiltered.lincurve(0, 1.5, 0, 5, -6);
		var lfoMin    = mod[\lfoMin] ? 0.1;
		var lfoMax    = mod[\lfoMax] ? 0.4;
		var lfoFreq   = m.gyroYFiltered.linlin(-1.0, 1.0, lfoMin, lfoMax);
		var filtSpeed = m.accelMassFiltered.lincurve(0, 2.5, 0.1, 1, 3);
		var t         = c[\now];
		var rays      = mod[\rays] ? 10;
		var near      = mod[\near] ? 26;
		var far       = c[\size];
		var breath    = mod[\breath] ? 0.14;
		var wob       = mod[\wobble] ? 0.05;
		var spread    = mod[\spread] ? 0.7;
		var alpha     = mod[\alpha] ? 0.45;
		rays.do({ |i|
			var ang     = (i / rays) * 2pi;
			var breathe = 1 + (sin((t * lfoFreq) + (i * spread)) * breath * a);
			var wobble  = 1 + (sin((t * filtSpeed * 0.2) + (i * 1.9)) * wob * a);
			var p0      = c[\pos] + Polar(near, ang).asPoint;
			var p1      = c[\pos] + Polar(far * breathe * wobble, ang).asPoint;
			c[\render].(Array.fill(10, { |j| p0.blend(p1, j / 9) }), 1, alpha, false);
		});
		nil
	});

	// THE single event. Adjust the tunnel here and nowhere else.
	(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
		shape: \tunnelWalls,
		sx: 0, sy: 0, ex: 0, ey: 0,
		startSize: 620,
		startWidth: 2.6,
		startColor: Color.hsv(0.82, 0.90, 0.55, 1.0),
		closed: false,
		duration: inf,
		modulation: (
			amp: 0,
			rays: 10,
			near: 26,
			lfoMin: 0.1,
			lfoMax: 0.4,
			breath: 0.14,
			wobble: 0.05,
			spread: 0.7,
			alpha: 0.45
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
	var filtSpeed = m.accelMassFiltered.lincurve(0,2.5,0.1,20,3);
	var lfoFreq = m.accelMassFiltered.lincurve(0,2.5,0.1,8,-1);
	var fmin = m.gyroYFiltered.linexp(-1.0,1.0,10,800);
	var fmax = m.gyroYFiltered.linexp(-1.0,1.0,100,8000);

	if(a<0.03,{a=0});
	if(a>0.9,{a=0.9});
    
    synth.set(\freq, (note + m.com.root).midicps);
	synth.set(\amp, a * 0.5);
	synth.set(\filtSpeed, filtSpeed);
	synth.set(\lfoFreq, lfoFreq);
	synth.set(\filtMin, fmin);
	synth.set(\filtMax, fmax);


	if(d.sensors.accelEvent.x > 2.0, {
	// if(m.accelMassFiltered > 1.0, {

		if(TempoClock.beats > (lastTime + 0.055),{
			// same expression the bsynth below takes for its \amp, so the
			// ring is the strength of this hit rather than a guess at it.
			var hitAmp = m.accelMassFiltered.lincurve(1,2.5,0.1,0.5,-2);

			lastTime = TempoClock.beats;

			bsynth = Synth(\warmPadMove2, [
				\freq, (note + m.com.root+ [0,-2,-5,7].choose).midicps * 4, 
				\amp, m.accelMassFiltered.lincurve(1,2.5,0.1,0.5,-2),
				\gate, 1,
				\atk, 0.3,
				\rel, 3.1,
				\filtMin, 8000,
				\filtMax, 12000,
				\filtSpeed, 0.1,
				\chorusRate, 0.1,
				\chorusDepth, 0.01,
				\detuneAmount, 0.0004
			]);


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
				sx: 0, sy: 0, ex: 0, ey: 0,
				startSize: hitAmp.linlin(0.1, 0.5, 40, 90),
				endSize: hitAmp.linlin(0.1, 0.5, 260, 480),
				sizeEnv: Env([0, 1], [1], -3),
				startWidth: hitAmp.linlin(0.1, 0.5, 4, 9),
				endWidth: 0.5,
				widthEnv: Env([0, 1], [1], -3),
				startColor: Color.hsv(0.82, 0.25, 1.0, 0.95),
				endColor: Color.hsv(0.82, 0.60, 1.0, 0.0),
				duration: 0.8
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
		[m.gyroYFiltered.lincurve(-1.0,1.0,0.0,1.0,-2)];

	// [m.rrateMassFiltered, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

