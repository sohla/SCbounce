
// unique name for pattern 
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});


var up = 0;
var rm = 0;
var buffer = Array.fill(10,{0}); 
//------------------------------------------------------------	
// SYNTH DEF
//------------------------------------------------------------	

(
	SynthDef(\eveSynth, { |out=0, freq=240, gate=1, amp=0.01, pan=0.0, attack=0.01, sustain=0.5, release=1.3|
	var env = EnvGen.kr(Env.adsr(attack, sustain, sustain, release), gate, doneAction:2);
	var sig = SinOsc.ar(freq,0,1.0)!2;
	var verb = FreeVerb2.ar(sig[0],sig[1],0.3 ,500);
	Out.ar(out, Pan2.ar(verb, pan, env * amp));
}).add;
);

// use to hear this synth onces
//x = Synth(\adamSynth);s.sendBundle(0.5,[\n_set,x.nodeID,\gate,0]);

//------------------------------------------------------------	
// PATTERN DEF
//------------------------------------------------------------	

Pdef(ptn,
	Pbind(
//        \degree, Pseq([0,2,4,6,8,7,5,3,1], inf),
        \degree, Pseq([0,2,4,6,8,7,5,3,1,0,1,2,3,4,5,6,7,6,7,5,6,4,5,3,4,2,3,1,2,0,1,5,4,2,4,3,1,3,2,0,1,7,6,0,5,4,0,3,2,0,1,1,1], inf),
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
		Pdef(ptn).set(\dur,0.05);
		Pdef(ptn).set(\octave,5);

		Pdef(ptn).set(\attack,0.001);
		Pdef(ptn).set(\sustain,0.27);
		Pdef(ptn).set(\release,1.92);

		// Pdef(ptn).set(\type,\midi);
		// Pdef(ptn).set(\midiout,mo);
		// Pdef(ptn).set(\chan,2);

		Pdef(ptn).play;

		~bla.();
	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 
		
		var nextRateMass = d.rrateEvent.sumabs;

		up = nextRateMass - d.rrateMass;

		if(up.floor > 0,{
			d.rrateMass = ~tween.(d.rrateEvent.sumabs,d.rrateMass,0.04);
		},{
			d.rrateMass = ~tween.(d.rrateEvent.sumabs,d.rrateMass,0.9);
		});

		d.accelMass = ~tween.(d.accelEvent.sumabs,d.accelMass,0.7);

		Pdef(ptn).set(\octave,4+(((d.gyroEvent.pitch / pi) + 0.5) * 5).floor);

		Pdef(ptn).set(\dur,(1 / (1 + d.accelMass.floor)) * 0.5);

		if(d.rrateMass  < 0.8,{
			Pdef(ptn).set(\amp,0.0);
		},{
			Pdef(ptn).set(\amp,d.rrateMass / 20.0);
		});
			Pdef(ptn).set(\amp,0.0);

	};

	//------------------------------------------------------------	
	// cleanup
	//------------------------------------------------------------	
	~deinit = {

		"deinit EVE".postln;
		Pdef(ptn).stop;
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
		//(((d.gyroEvent.pitch / pi) + 0.5) * 8).floor;
		//0.2;

		//1 / (1 + d.accelMass.floor);
		//d.rrateMass
		//up.floor





		var r = buffer.sum / buffer.size;


		rm = ~tween.(d.rrateEvent.sumabs / 4,rm,0.05);

		buffer = buffer.shift(1);
		buffer = buffer.put(0,d.rrateEvent.sumabs / 4);
		r
		//rm
	};
	

)

