var m = ~model;
var synth;
var note = 60;
var dur = 0.22;

// SHARED ACROSS THE QUARTET. All four voices read m.com.root, so a
// harmony change turns every hue together — this file is the one that
// WRITES it (see ~onEvent), the other three follow. Each voice keeps its
// own base hue; the root only nudges it, so the ensemble shifts as one
// without losing channel identity.
var rootHue = { (m.com.root ? 0).linlin(-2, 3, -0.06, 0.06) };

// one full turn of the wheel = 32 events = one root hold
// (16 riff notes .stutter(2)), so the riff and the harmony close together.
var turn = 32;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.1;
m.accelMassFilteredDecay = 0.99;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;
//------------------------------------------------------------

//------------------------------------------------------------

SynthDef(\funBass, {
    |out=0, freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 4.2, rm = 0.5|
    var osc1, osc2, osc3, env, filter, output;

    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: Done.freeSelf);
    osc1 = Saw.ar(freq, 1);
    osc2 = Pulse.ar(freq * 0.99, 0.3, 1);
    osc3 = SinOsc.ar(freq * 1.01, 0, 1);
    output = Mix([osc1, osc2, osc3]) * env * amp;
    filter = RLPF.ar(output, filtFreq, filtRes);
		filter = [filter.distort, filter.tanh];
    Out.ar(out, filter.softclip);
}).add;

SynthDef(\warmPad, {
	|out=0, gate=1, freq=440, amp=0.1,atk=0.03, dec=0.2, sus=0.8, rel=1.0,filtMin=500, filtMax=5000, filtSpeed=0.5,
	detuneAmount = 0.001,chorusRate=0.5, chorusDepth=0.01,pan=0, spread=0.2, lfoFreq=1|

    var sig, env, filt, chorus, numVoices=8, sub;
		var pulse = LFCub.ar(lfoFreq,pi,0.5,0.5);
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

    // Filter sweep
    filt = SinOsc.kr(filtSpeed).range(filtMin, filtMax);
    sig = RLPF.ar(sig, filt, 0.5);

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
	sub = LFTri.ar(freq/2, pi, 0.3).tanh;
	sig = Mix([sig, chorus.sum]) / (numVoices + 2);
  // sig = sig * env * amp;

    // Output with stereo spread
    sig = Splay.ar(sig, spread);
		// sig = GVerb.ar(sig.tanh * 0.2,4,0.1);
	Out.ar(out, (sig + sub) * amp.lag(0.3) * env * pulse);
}).add;


SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, decay=0.5, clickLevel=0.5, amp=0.5, dist = 5, filtFreq = 20, filtRes = 0.8,pan =0|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch;

	var sub = SinOsc.ar(freq * 0.5, pi, 1) * 1;
    // Pitch envelope
    pitch_contour = Line.kr(1, 0, 0.02);

    // Drum oscillator

	pch = freq * (1 + (pitch_contour * tension));
	drum_osc = SinOsc.ar([pch,pch*1.007], LFNoise2.ar([4,5],10,-10),0.8);

    // Click oscillator
    click_osc = LPF.ar(WhiteNoise.ar(1), 1500);

    // Drum envelope
    drum_env = EnvGen.ar(
        Env.perc(attackTime: 0.005, releaseTime: decay, curve: -4),
        doneAction: 2
    );

    // Click envelope
    click_env = EnvGen.ar(
        Env.perc(attackTime: 0.001, releaseTime: 0.01),
        levelScale: clickLevel
    );
	sig = (drum_osc * drum_env) + (click_osc * click_env);
	sig = (sig * dist).tanh.distort;
	sig = HPF.ar(sig, filtFreq)+ (sub * drum_env);
    // Mix and output
    Out.ar(out, Balance2.ar(sig[0], sig[1],pan,amp))
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// visual : the drive wheel, and the hub the whole quartet turns on.
	// This voice owns the harmony, so it owns the rotation — one full
	// revolution is one root hold (32 events), which is also exactly one
	// pass of the 16-note riff. Each event lays a spoke from hub to rim
	// with a head at the rim; marks linger and fade behind the current
	// one, so the accumulating arc IS the wheel going round. The Rest in
	// the dur cycle draws nothing, so the gap in the rim is the rest.
	//
	// Lineage: the cyclic/radial notations in the atlas (G8) — time as
	// angle, loopable with no seam — over register bands (G4) for the
	// shared vertical layout. Palette from atlas §0.5, saturated
	// primaries on black.
	//
	//   riff step   -> angle round the wheel   (\vstep -> \rotation)
	//   octave      -> rim radius              (\startSize)
	//   amp         -> head size + weight      (\modulation, \startWidth)
	//   m.com.root  -> hue, shared with all four voices
	//   Rest        -> nothing drawn           (\modulation rest)
	~vdef.(\driveSpoke, { |ev, c|
		var mod  = ev[\modulation] ? ();
		var hub  = c[\pos];
		var rim  = hub + (c[\size] @ 0);
		var head = mod[\head] ? 16;
		if((mod[\rest] ? false).not, {
			c[\render].(Array.fill(12, { |j| hub.blend(rim, j / 11) }), 0.45, 0.30, false);
			c[\draw].(\circle, (pos: rim, size: head), 1, 1);
		});
		nil
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \versatilePerc,
			\note, Pseq([0,10,5,4,7,7,2,5,4,4,-2,2,0,0,0,0].stutter(2) + 4, inf),
    		\dur, Pseq([0.22,0.22,Rest(0.22),0.22,0.22,0.22], inf),
			\octave,Pseq([3,4].stutter(1),inf),
			\root, Pseq([0,0,-2,0,0,3].stutter(32), inf),
			\decay,Pkey(\octave).squared * 0.05,
   			\pan, Pxrand([-0.5,0.5], inf),
   			\filtRes, 1.0,//Pwhite(0.4,0.7),

			\type, \customVisualEvent,
			\shape, \driveSpoke,
			\vstep, Pseries(0, 1, inf),
			\rotation, ((Pkey(\vstep) % turn) / turn * 2pi) - 0.5pi,
			\startSize, Pfunc({ |e| (e[\octave] ? 3).linlin(3, 4, 150, 235) }),
			\endSize, Pkey(\startSize),
			\startWidth, Pfunc({ |e| (e[\amp] ? 0.3).linlin(0, 1, 0, 7) }),
			\endWidth, 0.5,
			\startColor, Pfunc({ |e| Color.hsv((0.03 + rootHue.()).wrap(0, 1), 0.88, 1.0, 1.0) }),
			\endColor, Pfunc({ |e| Color.hsv((0.03 + rootHue.()).wrap(0, 1), 1.0, 0.35, 0.0) }),
			\duration, 0.95,
			\modulation, Pfunc({ |e|
				(rest: e.isRest, amp: 0,
					head: (e[\amp] ? 0.3).linlin(0, 1, 7, 22))
			}),

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:dur);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	// synth.free;
	// synth.set(\gate, 0);
};

//------------------------------------------------------------
~onEvent = {|e|
	if(e.root != m.com.root,{
		// "key change".postln;
		synth.set(\freq, (note + e.root).midicps);
	});
	m.com.root = e.root;
	m.com.dur = e.dur;
};

//------------------------------------------------------------
~next = {|d|

	// var dur = 0.5 * 2.pow(m.accelMassFiltered.lincurve(0,2,0,3,-1).floor).reciprocal;
	var a = m.accelMassFiltered.lincurve(0,2,0,1,-3);
	var filtSpeed = m.accelMassFiltered.lincurve(0,2.5,0.1,20,3);
	var lfoFreq = m.accelMassFiltered.lincurve(0,2.5,0.1,18,-1);
	var filtFreq = m.accelMassFiltered.lincurve(0,2.5,10,1000,-3);//d.sensors.gyroEvent.z.abs.linlin(0.3,0.7,30,200);

	if(a<0.03,{a=0});

	// synth.set(\amp, a * 0.4);
	synth.set(\filtSpeed, filtSpeed);
	synth.set(\lfoFreq, lfoFreq);

	// Pdef(m.ptn).set(\filtFreq, filtFreq);
	// Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, a * 1);
	
	// if(m.accelMassFiltered > 0.1,{
	// 	if( Pdef(m.ptn).isPlaying.not,{
	// 		Pdef(m.ptn).resume(quant:dur);
	// 	});
	// },{
	// 	if( Pdef(m.ptn).isPlaying,{
	// 		Pdef(m.ptn).pause();
	// 	});
	// });

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
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

