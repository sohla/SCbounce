var m = ~model;
var synth;
var buffer;
var bl = false;

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.08;

//------------------------------------------------------------
SynthDef(\sintri, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.001, decay=0.03, sustain=0.8, release=0.9, gate=1,cutoff=20000, rq=1, rezf=200|

	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:0);
	var sub = LFTri.ar(freq/2,0,0.2).tanh;
	var sig = SinOsc.ar(freq * [1.0,1.03],0,0.5) + sub;
	
    // sig = RLPF.ar(sig, cutoff, rq) + sub;
		// sig = Resonz.ar(sig, rezf.lag(0.4), 0.05, 5)* amp.lag(0.9);
		// sig = AllpassN.ar(sig, 0.1, [0.09, 0.08], 8);
		// sig = JPverb.ar(sig,1, modDepth: 0.1, modFreq: 4.0, low: 1.0);

    Out.ar(out, sig * env);
}).add;

SynthDef(\funBass, {
    |out=0, freq = 440, gate = 1, amp = 0.07, filtFreq = 2000, filtRes = 0.5, envAtk = 0.01, envDec = 0.1, envSus = 0.8, envRel = 1.2, rm = 0.5|
    var osc1, osc2, osc3, env, filter, output;

    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: 0);
    osc1 = Saw.ar(freq, 1);
    osc2 = Pulse.ar(freq * 0.99, 0.3, 1);
    osc3 = SinOsc.ar(freq * 1.01, 0, 1);
    output = Mix([osc1, osc2, osc3]) * env * amp;
    filter = RLPF.ar(output, filtFreq.lag(0.7), filtRes);
		filter = [filter.distort, filter.tanh];
    Out.ar(out, filter.softclip);
}).add;


SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, decay=3.5, clickLevel=0.5, amp=0.05, dist = 5, filtFreq = 20, filtRes = 0.8,pan =0, gate=1|
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
        Env.perc(attackTime: 0.005, releaseTime: decay, curve: -4),gate,
        doneAction: 0
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
    Out.ar(out, Balance2.ar(sig[0], sig[1],pan,amp))
}).add;
//------------------------------------------------------------
~init = ~init <> {
		synth = Synth(\funBass,[\gate, 0 ]);
};

~deinit = ~deinit <> {
	synth.free;
	// synth.set(\gate, 0);
	buffer.free;
};
//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMassFiltered.linlin(0,2,0.00001,1);
	var filtFreq = m.accelMassFiltered.lincurve(0.0,2.5,20,5040,2);

  var notes = [29,31,34,38,39];
  var colors = [Color.red, Color.green, Color.blue, Color.yellow, Color.cyan];
  var ni = (d.sensors.gyroEvent.z / pi).lincurve(-0.5,0.5,0,notes.size,0).floor;//cw/ccw
  // var ni = (d.sensors.gyroEvent.y / pi).lincurve(-1.0,1.0,0,notes.size,0).floor;//left/right
  // var ni = (d.sensors.gyroEvent.x / pi).lincurve(-1.0,1.0,notes.size,0,0).floor; //up/down

	if(amp < 0.04, {amp = 0});

  if(d.sensors.digiInEvent[0] == 1, {
    
    var event = (
      type: \customEvent,
      amp: 0,
      viewID: d.port,
      shape: \circle,
      fill: false,
      startSize: filtFreq.linlin(20,5040,100,200),
      endSize: filtFreq.linlin(20,5040,100,200),
      duration: 0.6,
      startColor: colors[ni],
      endColor: colors[ni].lighten(0.1),
      sx: 300,
      sy: 300,
      ex: 300,
      ey: 300,
      rotation: amp * pi,
      modulation: (
        type: \normal,
        freq: 2,
        amp: 80 * amp,
        harmonics: 2
    ),
    );

    event.play;
    synth.set(\gate, 1);
  }, {
    synth.set(\gate, 0);
  });

  synth.set(\freq, notes[ni].midicps);
  synth.set(\filtFreq, filtFreq);
    
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
	// [m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
