var m = ~model;
var synth;
var bsynth;
var note = 48 + 4;
var lastTime = 0;
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

    // Filter sweep
    filt = SinOsc.kr(filtSpeed.lag(1.5)).range(filtMin, filtMax);
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
	sub = LFTri.ar(freq * (3/2), pi, 13).tanh * 0.02;
	sig = Mix([sig, chorus.sum]) / (numVoices + 2);
  // sig = sig * env * amp;

    // Output with stereo spread
    sig = Splay.ar(sig, spread);
		sig = GVerb.ar(sig.tanh * 0.2,4,0.1);
	Out.ar(out, (sig + sub) * Amplitude.kr(amp,0.03,0.8) * env * exciter);
}).add;


//------------------------------------------------------------
// intial state
//------------------------------------------------------------
~init = ~init <> {

	synth = Synth(\warmPadMove2, [
		\freq, note.midicps, 
		\amp, 0,
		\gate, 1,
    \atk, 1.02,
    \rel, 1.8,
    \filtMin, 800,
    \filtMax, 8000,
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

	if(a<0.03,{a=0});
	if(a>0.9,{a=0.9});
    
    synth.set(\freq, (note + m.com.root).midicps);
	synth.set(\amp, a * 0.4);
	synth.set(\filtSpeed, filtSpeed);
	synth.set(\lfoFreq, lfoFreq);


	// if(d.sensors.accelEvent.y > 1.6, {
	// // if(m.accelMassFiltered > 1.0, {

	// 	if(TempoClock.beats > (lastTime + 0.11),{
	// 		lastTime = TempoClock.beats;

	// 		bsynth = Synth(\warmPadMove2, [
	// 			\freq, (note + m.com.root+ [0,-2,-5].choose).midicps * 4 , 
	// 			\amp, 0.2,
	// 			\gate, 1,
	// 			\atk, 0.1,
	// 			\rel, 3.1,
	// 			\filtMin, 800,
	// 			\filtMax, 8000,
	// 			\filtSpeed, 0.1,
	// 			\chorusRate, 0.01,
	// 			\chorusDepth, 0.0001,
	// 			\detuneAmount, 0.0004
	// 		]);

	// 		{
	// 			bsynth.set(\gate, 0);
	// 		}.defer(0.1);
	// 		// NodeWatcher.register(bsynth);

	// 	},{
	// 	});
	// 	// event.play;
	// });


	// if(m.accelMassFiltered > 0.5,{

	// 		synth.set(\trig, 1);
	// 	if( (TempoClock.beats-oldTime) > 0.11, {
	// 		"t".postln;
	// 		oldTime = TempoClock.beats;
	// 		synth.set(\trig, 0);

	// 	},{
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
	// [m.rrateMassFiltered];
	[d.sensors.accelEvent.x.abs];
	// [m.rrateMassFiltered, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

