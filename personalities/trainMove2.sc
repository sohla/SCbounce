var m = ~model;
var synth;
var bsynth;
var note = 48 + 4;
var lastTime = 0;

// SHARED ACROSS THE QUARTET. trainBass2 writes m.com.root; this voice
// reads it, so a harmony change turns every hue in the ensemble together.
var rootHue = { (m.com.root ? 0).linlin(-2, 3, -0.06, 0.06) };

// visual only : when the last ground layer was laid. This is a genuine
// accumulator over frames — it cannot be recomputed from m or the event.
var vizTime = 0;

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
~init = ~init <> {

	// visual : the ground. This voice is one long-lived pad, not a
	// pattern, so it gets the only continuous mark in the quartet — a low
	// contour the other three sit above. It has no events of its own, so
	// ~next lays a fresh layer every 0.4s and each fades over 1.6s; the
	// four or so live layers drift apart slightly as they age, and that
	// separation is the motion — the ground sliding past, drawn by its
	// own history rather than by an animation.
	//
	// Nothing here is relayed: the contour recomputes a, lfoFreq and
	// filtSpeed from m with the same expressions ~next feeds the synth,
	// so the line is provably the drone rather than a decoration beside
	// it. The slow wave is the pad's LFO; the fine ripple on top is its
	// filter sweep.
	//
	// Lineage: topographic contour, atlas grammar G7, laid as a register
	// band (G4) under the G8 hub the other three turn on. Palette from
	// atlas §0.5, saturated primaries on black — this voice takes the
	// darkest, coolest end so it reads as ground, never as figure.
	//
	//   accel      -> how far the ground heaves   (a -> lift)
	//   lfoFreq    -> wave travel rate
	//   filtSpeed  -> fine ripple rate
	//   m.com.root -> hue, shared with all four voices
	~vdef.(\groundBand, { |ev, c|
		var mod       = ev[\modulation] ? ();
		var a         = m.accelMassFiltered.lincurve(0, 1.5, 0, 1, -6);
		var lfoFreq   = m.accelMassFiltered.lincurve(0, 2.5, 0.1, 8, -1);
		var filtSpeed = m.accelMassFiltered.lincurve(0, 2.5, 0.1, 20, 3);
		var t         = c[\now];
		var half      = c[\size];
		var lift      = (mod[\lift] ? 70) * a;
		var waves     = mod[\waves] ? 3;
		var n         = 32 * a;
		Array.fill(n, { |i|
			var u = i / (n - 1);
			var x = (u - 0.5) * 2 * half;
			var y = sin((u * waves * 2pi) + (t * lfoFreq)) * lift;
			var ripple = sin((u * waves * 5 * 2pi) - (t * filtSpeed * 0.25)) * lift * 0.18;
			c[\pos] + (y @ (x + ripple))
		})
	});

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

~onHit = {|state|
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

	if(TempoClock.beats > (vizTime + 0.4), {
		vizTime = TempoClock.beats;
		(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
			shape: \groundBand,
			sx: 0.06, ex: -0.06,
			sy: 0.55, ey: 0.55,
			startSize: 560, endSize: 560,
			startWidth: 3.5, endWidth: 0.8,
			startColor: Color.hsv((0.62 + rootHue.()).wrap(0, 1), 0.75, 0.85, 0.5),
			endColor: Color.hsv((0.62 + rootHue.()).wrap(0, 1), 0.9, 0.5, 0.0),
			closed: false,
			duration: 1.6,
			modulation: (amp: 0, lift: 70, waves: 3)
		).play;
	});


	if(d.sensors.accelEvent.x > 2.0, {
	// if(m.accelMassFiltered > 1.0, {

		if(TempoClock.beats > (lastTime + 0.055),{
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

		},{
		});
	});

};

~nextMidiOut = {|d|
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

