var m = ~model;
var synth;
var note = 43+24;
m.accelMassFilteredAttack = 0.1;
m.accelMassFilteredDecay = 0.99;

SynthDef(\funBass, {
    |out=0, freq = 440, gate = 1, amp = 0.8, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.7, envRel = 0.2, rm = 0.5|
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
		sig = GVerb.ar(sig.tanh * 0.2,4,0.1);
	Out.ar(out, (sig + sub) * amp.lag(0.3) * env * pulse);
}).add;


SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, decay=0.5, clickLevel=0.5, amp=0.5, dist = 5, filtFreq = 2000, filtRes = 0.8|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch;

    // Pitch envelope
    pitch_contour = Line.kr(1, 0, 0.02);

    // Drum oscillator

	pch = freq * (1 + (pitch_contour * tension));
	drum_osc = SinOsc.ar([pch,pch*1.004], LFNoise2.ar([4,5],10,-10),0.5);

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
	sig = HPF.ar(sig, filtFreq);
    // Mix and output
    Out.ar(out, Pan2.ar(sig,0,amp))
}).add;
//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\instrument, \versatilePerc,
			\note, Pseq([0,2,7,10,5], inf),
			// \note, Pseq([5,9,4,2], inf),
			\octave,Pseq([2,3,4].stutter(2),inf),
			\root, Pseq([0,3,-2,1,-1].stutter(32), inf),
			\envAtk,0.001,
			\envDec,0.3,
			\envSus, 0.0,
			\envRel,Pkey(\octave).squared * 0.05,
   		\amp, 0.4,
   		\filtRes, Pwhite(0.1,0.2),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		)
	);

	Pdef(m.ptn).play(quant:0.125);
	synth = Synth(\warmPad, [
		\freq, note.midicps, 
		\amp, 0.1,
		\gate, 1,
    \atk, 1.02,
    \rel, 1.8,
    \filtMin, 800,
    \filtMax, 8000,
    \filtSpeed, 0.1,
    \chorusRate, 0.001,
    \chorusDepth, 0.0001,
		\detuneAmount, 0.0002
	]);
};


//------------------------------------------------------------
// triggers
//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	synth.free;

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
	var a = m.accelMassFiltered.lincurve(0,3,0,1,-6);
	var filtSpeed = m.accelMassFiltered.lincurve(0,2.5,0.1,20,3);
	var lfoFreq = m.accelMassFiltered.lincurve(0,2.5,0.1,8,-1);
	var filtFreq = d.sensors.gyroEvent.z.abs.linlin(0.4,1,30,600);

	if(a<0.03,{a=0});
	if(a>0.9,{a=0.9});

	synth.set(\amp, a * 0.5);
	synth.set(\filtSpeed, filtSpeed);
	synth.set(\lfoFreq, lfoFreq);

	Pdef(m.ptn).set(\filtFreq, filtFreq);
	Pdef(m.ptn).set(\dur, dur);
	
	if(m.accelMassFiltered > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.125);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
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
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	[d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

