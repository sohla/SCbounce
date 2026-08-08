var m = ~model;
var synth;
var bl = false;
var frame = 0;
var bassLines = [[0,10,17,16],[0]];
var bassLine = bassLines[0];

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.38;

//------------------------------------------------------------
SynthDef(\simple, {|bufnum=0, out=0, amp=0.5, rate=1, start=0, pan=0, freq=440,
    attack=0.001, decay=0.03, sustain=0.1, release=0.09, gate=1,cutoff=20000, rq=1, rezf=200|

	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction:0);
	var sig = SinOsc.ar(freq,0,0.5);
    Out.ar(out, sig * env);
}).add;

SynthDef(\funBass, {
    |out=0, freq = 440, gate = 1, amp = 0.2, filtFreq = 2000, filtRes = 0.5, envAtk = 0.001, envDec = 0.1, envSus = 0.8, envRel = 1.2, rm = 0.5|
    var osc1, osc2, osc3, env, filter, output;
    freq = freq.lag(0.7);
    env = EnvGen.ar(Env.adsr(envAtk, envDec, envSus, envRel), gate, doneAction: 0);
    osc1 = LFTri.ar(freq * 4.98, 0,0.3);
    osc2 = LFSaw.ar(freq * 1.99, 0, 1);
    osc3 = SinOsc.ar(freq * 1.01, 0, 1);
    output = Mix([osc1, osc2, osc3]) * env * amp;
    filter = RLPF.ar(output, filtFreq.lag(0.7), filtRes);
		// filter = [filter.distort, filter.tanh];
    filter = DelayC.ar(filter, 0.09, [0.03,0.09], 1, filter);
    Out.ar(out, filter.softclip);
}).add;


SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, decay=0.5, clickLevel=0.5, level=0.1, dist = 5, filtFreq = 20, filtRes = 0.8,pan =0, gate=1|
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
        doneAction: 2
    );

    // Click envelope
    click_env = EnvGen.ar(
        Env.perc(attackTime: 0.001, releaseTime: 0.01), 
        levelScale: clickLevel
    );
	sig = (drum_osc * drum_env) + (click_osc * click_env);
	sig = (sig * dist).tanh.distort;
	sig = HPF.ar(sig, filtFreq) * level;
    // Mix and output
    Out.ar(out, Balance2.ar(sig[0], sig[1],pan));
}).add;
//------------------------------------------------------------
~init = ~init <> {
  var size = 60;
	Pdef(m.ptn,
		Pbind(
			\instrument, \versatilePerc,
			\scale, Scale.major,
			\note, Pseq(bassLine + 2, inf),
      \dur, 0.2,
      \octave, 4,
      \dist, 10,
      // \filtFreq, 100,
      \filtRes, 0.1,
      \tension, 0.1,
      // \decay, 0.2,


			\type, \customVisualEvent,
			\sx, Pseq([-1,1,1,-1] * 0.8, inf),
			\sy, Pseq([-1,-1,1,1] * 0.8, inf),
			\ex, Pseq([-1,1,1,-1] * 0.1, inf),
			\ey, Pseq([-1,-1,1,1] * 0.1, inf),
			\endSize, 1,
      \duration, 2.0,
			\fill, false,
      \startWidth, 1,
      \endWidth, 3,
      

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);
	Pdef(m.ptn).play(quant:0.1);

		synth = Synth(\funBass,[\gate, 0 ]);
    // NodeWatcher.register(synth);
};

~deinit = ~deinit <> {
  	Pdef(m.ptn).remove;

	synth.free;
	// synth.set(\gate, 0);
	// buffer.free;
};

//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
	// Pdef(m.ptn).set(\root, m.com.root);
	// Pdef(m.ptn).set(\sx, ((e.octave * 12) + e.note).linlin(40,100,0,600));
	// ((e.octave * 12) + e.note).linlin(40,100,0,600).postln;
	frame = frame + 1;
};


//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0,2.5,0.00001,0.14,-4);
	var level = m.accelMassFiltered.lincurve(0,2.5,0.01,0.5,-4);
	var filtFreq = m.accelMassFiltered.lincurve(0.0,2.5,20,5040,2);
	var dcy = m.accelMassFiltered.lincurve(0.0,2.5,0.2,1.5,-2);

  var notes = [26,19,17] + 12;
  var colors = [Color.red, Color.green, Color.blue, Color.yellow, Color.cyan];
  var ni = (d.sensors.gyroEvent.z / pi).lincurve(-0.5,0.5,0,notes.size-1,1).asInteger;//cw/ccw
  var shapes = [\square, \triangle, \hexagon];
  var bassLine = bassLines[0];
  // var ni = (d.sensors.gyroEvent.y / pi).lincurve(-1.0,1.0,0,notes.size,0).floor;//left/right
  // var ni = (d.sensors.gyroEvent.x / pi).lincurve(-1.0,1.0,notes.size,0,0).floor; //up/down
  // (d.  sensors.gyroEvent.z / pi).lincurve(-0.5,0.5,0,notes.size-1,1).asInteger.postln;
	// if(amp < 0.04, {amp = 0});

  synth.set(\amp, amp*0.8);
  synth.set(\freq, notes[ni].midicps);
  synth.set(\filtFreq, filtFreq);

  Pdef(m.ptn).set(\root, notes[ni]-12-23-3);
  Pdef(m.ptn).set(\filtFreq, filtFreq);
  Pdef(m.ptn).set(\decay, dcy);
  Pdef(m.ptn).set(\level, level*1.5);
  Pdef(m.ptn).set(\shape, shapes[ni % shapes.size]);
  Pdef(m.ptn).set(\startSize, 30 + (100 * level));

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\startColor, Color.hsv((frame/10.0).mod(1.0),1,1.0, 0.5 + level));
	Pdef(m.ptn).set(\endColor, Color.hsv((frame/10.0).mod(1.0),1,1.0,0.2));
	Pdef(m.ptn).set(\rotation, (pi/60) * frame);
	Pdef(m.ptn).set(\modulation, (
			type: \radial,
			freq: 10,
			amp: level * 20,
			harmonics: 1
	));


  if(d.sensors.digiInEvent[0] == 1, {
    
    var event = (
      type: \customVisualEvent,
      amp: 0,
      viewID: d.port,
      shape: shapes[ni % shapes.size],
      fill: false,
      rotate: filtFreq.linlin(20,5040,0.0,pi),
      startSize: filtFreq.linlin(20,5040,200,300),
      endSize: filtFreq.linlin(20,5040,10,20),
      duration: 0.6,
      startColor: Color.new255(amp.lincurve(0.001,0.06,100,255,-3), 255, 0, amp.lincurve(0.004,0.06,1,255,-3)),//colors[ni],
      endColor: Color.new255(225, 5, 250,0),//colors[ni].lighten(0.1),
      startWidth: 3,
      sx: 0,
      sy: 0,
      ex: 0,
      ey: 0,
      rotation: (amp - 0.17.half.half) * pi,
      modulation: (
        type: \normal,
        freq: 2,
        amp: 80 * amp * 10,
        harmonics: 2
    ),
    );

    // event.play;
    if(bl == false, {
      bl = true;
      synth.set(\gate, 1);
      // synth.postln;
    });

  }, {
    bl = false;
    synth.set(\gate, 0);
  });

    
};
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [m.rrateMass * 0.1, m.rrateMassFiltered * 0.1];
  // [(d.sensors.gyroEvent.z / pi).lincurve(-0.5,0.5,0,5,1).floor] / 5;
	[m.accelMass * 0.3, m.accelMassFiltered * 0.5];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
