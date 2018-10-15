
// unique name for pattern 
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});


var up = 0;
var rm = 0;
var buffer = Array.fill(20,{0}); 
var sbuf = Array.fill(2,{0}); 
var bufavg = 0;

var release = 0;
var attack = 0;
//------------------------------------------------------------	
// SYNTH DEF
//------------------------------------------------------------	

(
	SynthDef(\eveSynth, { |out=0, freq=240, gate=1, amp=0.01, pan=0.0, attack=0.01, sustain=0.1, release=1.3|
	var env = EnvGen.kr(Env.adsr(attack, sustain, sustain, release), gate, doneAction:2);
	var sig = LFTri.ar(freq,0,1.0)!2;
	var verb = FreeVerb2.ar(sig[0],sig[1],0.3 ,500);
	Out.ar(out, Pan2.ar(verb, pan, env * amp));
}).add;
);

// use to hear this synth onces
//x = Synth(\eveSynth);s.sendBundle(0.5,[\n_set,x.nodeID,\gate,0]);

//------------------------------------------------------------	
// PATTERN DEF
//------------------------------------------------------------	

Pdef(ptn,
	Pbind(
//        \degree, Pseq([0,2,4,6,8,7,5,3,1], inf),
//        \degree, Pseq([0,2,4,6,8,7,5,3,1,0,1,2,3,4,5,6,7,6,7,5,6,4,5,3,4,2,3,1,2,0,1,5,4,2,4,3,1,3,2,0,1,7,6,0,5,4,0,3,2,0,1,1,1], inf),
		\note, Prand([0,2,4,6,8,10],inf),	
		//\note,  PSaw(4, 10, PLFTri(0.1,24,12)),	
		\args, #[]
//		\amp, Pexprand(0.1,0.4,inf),
//		\pan, Pwhite(-0.8,0.0,inf)
));

// use this to test patter/synth with default gui
// Pdef(ptn).play.gui;
// Pdef(ptn).set(\instrument,\eveSynth);
// Pdef(ptn).set(\dur,0.2);
// Pdef(ptn).set(\octave,4);

// Pdef(ptn).set(\attack,0.001);
// Pdef(ptn).set(\sustain,0.27);
// Pdef(ptn).set(\release,0.92);



(
	~init;
	

	//------------------------------------------------------------	
	// how ofter does ~next() get called from engine
	//------------------------------------------------------------	
	~secs = 0.03;

	//------------------------------------------------------------	
	// intial state
	//------------------------------------------------------------	
	~init = { |mo|

		"init EVE".postln;

		Pdef(ptn).set(\instrument,\eveSynth);
		Pdef(ptn).set(\dur,0.2);
		Pdef(ptn).set(\octave,5);

		Pdef(ptn).set(\attack,0.1);
		Pdef(ptn).set(\sustain,0.17);
		Pdef(ptn).set(\release,0.92);

		// Pdef(ptn).set(\type,\midi);
		// Pdef(ptn).set(\midiout,mo);
		// Pdef(ptn).set(\chan,2);

		Pdef(ptn).play;

		//~bla.();
	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 

		//attack = ~tween.(d.rrateEvent.sumabs,attack,0.9);
		release = ~tween.(d.rrateEvent.sumabs,release,0.7);

		if(up.isPositive,{
			d.rrateMass = ~tween.(d.rrateEvent.sumabs,d.rrateMass,0.75);
		},{
			d.rrateMass = ~tween.(d.rrateEvent.sumabs,d.rrateMass,0.35);
		});

		d.accelMass = ~tween.(d.accelEvent.sumabs,d.accelMass,0.7);
		d.gyroMass = ~tween.(d.gyroEvent.sumabs,d.gyroMass,0.7);

		Pdef(ptn).set(\octave,3+(((d.gyroEvent.yaw / pi) + 0.5) * 4).floor);

		Pdef(ptn).set(\dur,(1 / (1 + d.accelMass.floor.squared)) * 1);

		if(bufavg  < 0.2,{
			Pdef(ptn).set(\amp,0.0);
		},{
			Pdef(ptn).set(\amp,d.rrateMass / 20.0);
		});
			//Pdef(ptn).set(\amp,0.0);

	};

	//------------------------------------------------------------	
	// cleanup
	//------------------------------------------------------------	
	~deinit = {

		"deinit EVE".postln;
		Pdef(ptn).stop;
		Pdef(ptn).clear;
	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = -1;
	~plotMax = 1;

	//------------------------------------------------------------	
	// utility for output to a plotter : returns a value that
	// that will be put at the end of plotters data array
	//------------------------------------------------------------	

	~plot = { |d|

		var r = buffer.sum / buffer.size;
		var bs;

		var slope, step;
		//(sum<0.4).if({sum=0});

		rm = ~tween.(attack,rm,0.97);

		buffer = buffer.shift(1);
		buffer = buffer.put(0,release);

		slope = (buffer[0] - buffer[1]) * 5;

		sbuf = sbuf.shift(1);
		sbuf = sbuf.put(0,slope);

		step =  (sbuf[0] - sbuf[1]);

		attack = ~tween.(slope,attack,0.19);
		// bs = buffer.sum / (buffer.size-1); 
		// (bs >= bufavg).if({up= 0.5},{up= -0.5});
		// bufavg = bs; 
		bufavg = release.linlin(0,1,0,5).round * 0.19;

		
		// bs = buffer.mean;

		[release * 0.1, bufavg, d.rrateMass / 20.0 ]
	};
	

)

