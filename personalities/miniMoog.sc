
var m = ~model;

// growth curve for the visual circles: fast out, slow settle
var sizeEnv = Env([0, 1], [1], [-3]);
// hold the bright colour while the circle grows, then fade late - with a
// linear fade the big end of the growth is already invisible
var colorEnv = Env([0, 1], [1], [3]);

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.9;
m.rrateMassFilteredDecay = 0.5;

//------------------------------------------------------------
SynthDef(\miniMoog, {
	|freq = 440, amp = 0.5, attack = 0.1, decay = 0.2, sustain = 0.7, release = 0.3, gate = 1, filterFreq = 800, fq=0.5, pan = 0|

    var env, osc, filt, sig;
    env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	osc = Saw.ar([freq, freq * 1.004],1) + SinOsc.ar([freq-1, freq -1 * 0.005],0,1) + LFTri.ar([freq+1, freq * 1.004],0,1);
    filt = RLPF.ar(osc.tanh, filterFreq, fq).tanh;
    sig = filt * env * amp * 0.5;
    sig = Pan2.ar(sig, pan);
    Out.ar(0, sig.tanh);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// A growing, wobbling circle. Radius is modulated around the ring by
	// `lobes` and rotates over time by `spin`, so it breathes rather than
	// just scaling. Live-coded: edit and save, next frame draws the change.
	//
	//   c    : (pos: radius: width: color: normTime: now: bounds:) per frame
	//          NB `radius`, not `size` - `size` is a real Collection method
	//          and c.size would return the entry count, not the value.
	//   ev   : the whole visual event, incl. ev[\vargs] custom params
	~vdef.(\blob, { |ev, c|
		var n = 48;
		var va = ev[\vargs] ? ();
		var wob = va[\wobble] ? 0.3;
		var lobes = va[\lobes] ? 5;
		var spin = va[\spin] ? 3;
		var pt = { |i|
			var th = i / n * 2pi;
			var r = c.radius * (1 + (wob * sin((th * lobes) + (c.now * spin))));
			c.pos + Polar(r, th).asPoint
		};
		Pen.width = c.width;
		Pen.strokeColor = c.color;
		Pen.moveTo(pt.(0));
		(1..n).do { |i| Pen.lineTo(pt.(i)) };	// i == n lands back on th = 2pi
		if(ev[\fill] == true, { Pen.fill }, { Pen.stroke });
	});

	Pdef(m.ptn,
		Pbind(
			\instrument, \miniMoog,
            // \note, Pseq([0,5,2,9,4], inf),
            \note, Pseq([0,12,10,7,2] + 2, inf),

            \octave, Pseq([1,2,3].stutter(2) + 2, inf),
            // \root, Pseq([0,4,8].stutter(60), inf),
            \root, Pseq([0].stutter(60), inf),
            \amp, 0.1,
            \attack, Pwhite(0.02,0.09),
            \decay, 0.1,
            \sustain, 0.1,
            \release, Pwhite(0.1,2.4),
            \fq, 0.3,
            \pan, Pseq([-0.5, 0.5], inf),

			// ---- one circle per note ----------------------------------
			// \customVisualEvent emits the shape then replays the event as
			// a \note, so this stays a normal synth line as well.
			\type, \customVisualEvent,
			\shape, \blob,
			\fill, false,
			\sx, 0, \sy, 0,		// drift with the stereo position
			\ex, 0, \ey, 0,
			\startSize, 20,
			\endSize, 220,
			// Env must be wrapped: Env defines asStream, so a bare Env in a
			// Pbind gets streamed as its LEVELS (0, 1, ...) instead of being
			// passed through as the envelope object.
			\sizeEnv, Pfunc({ sizeEnv }),
			\duration, 2.2,
			\startWidth, 2,
			\endWidth, 0.4,
			\startColor, Pfunc({ |e| Color.hsv(((e[\note] ? 0) / 12.0).mod(1.0), 0.85, 1.0, 0.9) }),
			\endColor,   Pfunc({ |e| Color.hsv(((e[\note] ? 0) / 12.0).mod(1.0), 0.85, 1.0, 0.0) }),
			\colorEnv,   Pfunc({ colorEnv }),	// wrapped: see \sizeEnv above
			// -----------------------------------------------------------

			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);
	Pdef(m.ptn).play(quant:0.1);
	Pdef(m.ptn).pause;
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
//------------------------------------------------------------
~onEvent = {|e|
	m.com.root = e.root;
};


//------------------------------------------------------------
~next = {|d|

	var dur = m.rrateMassFiltered.linexp(0,0.3,0.35,0.07);

	// var ff = (d.sensors.gyroEvent.x/pi).linexp(-0.5,0.5,300,10000); 
	// var oct = (d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,3.0,8.0); //left right

	var amp = m.accelMassFiltered.lincurve(0,2.5,-28,-13,-3);
	var atk = m.accelMassFiltered.lincurve(0,2.5,0.03,0.0001,-3);
	var rel = m.accelMassFiltered.lincurve(0,2.5,0.01,1.0,-1);
	var ff = m.accelMassFiltered.linexp(0,0.5,60,18000);

	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\atk, atk);
	Pdef(m.ptn).set(\rel, rel);
    Pdef(m.ptn).set(\filterFreq, ff);

	// visuals : route to this device, and let movement drive the wobble
	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\vargs, (
		wobble: m.accelMassFiltered.lincurve(0, 2.5, 0.004, 0.04, -2),
		lobes: 5,
		spin: 3
	));

	if(m.rrateMassFiltered > 0.01,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.15);
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

	// ACCEL
	// [m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	
	// ROTATE
	[m.rrateMass, m.rrateMassFiltered.linlin(0,1,0,1)];

	// X axis
	// [d.sensors.gyroEvent.x/pi]; // norm
	
	// Y axis
	// [d.sensors.gyroEvent.y/pi]; // norm

	// Z axis
	// [d.sensors.gyroEvent.z/(pi/2)]; // norm

	// device [I• ]
	// [(d.sensors.gyroEvent.x/pi).linlin(-0.5,0.5,0.9,-0.9)]  //up down
	// [(d.sensors.gyroEvent.y/pi).linlin(-0.4,0.4,0.9,-0.9)]  //left right
	// [(d.sensors.gyroEvent.z/(pi/2)).linlin(-0.3,1.0,-0.9,0.9)]  //wrist rotate


};




// •Pitch + Duration 
// •Pitch + Timbre 
// •Pitch + Dynamics 
// Duration + Timbre 
// Duration + Dynamics 
// Timbre + Dynamics
