
// unique name for pattern 
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});

var smooth = 0;
var synth;
//------------------------------------------------------------	
// SYNTH DEF
//------------------------------------------------------------	

(
	SynthDef(\adamSynth, { |out=0, freq=240, gate=1, amp=0.3, pan=0.0, attack=0.01, sustain=0.5, release=1.3|
	var env = EnvGen.kr(Env.adsr(attack, sustain, sustain, release), gate, doneAction:2);
	var sig = SinOsc.ar(freq,0,1.0)!2;
	var verb = FreeVerb2.ar(sig[0],sig[1],0.3 ,500);
	Out.ar(out, Pan2.ar(sig, pan, env * amp));
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
        //\degree, Pseq([0,1,2,0,2,0,2,1,2,3,3,2,1,3,2,3,4,2,4,2,4,3,4,5,5,4,3,5,4,0,1,2,3,4,5,5,1,2,3,4,5,6,6,2,3,4,5,6,7,6,5,5,3,6,4,7,4,3,1], inf),
		\note, Prand([0,2,7],inf),
		\args, #[],
		\amp, Pexprand(0.1,0.4,inf),
		\pan, Pwhite(-0.8,0.8,inf)
));

// use this to test patter/synth with default gui
// Pdef(ptn).play.gui;
// Pdef(ptn).set(\instrument,\adamSynth);
// Pdef(ptn).set(\dur,0.2);
// Pdef(ptn).set(\octave,4);

// Pdef(ptn).set(\attack,0.001);
// Pdef(ptn).set(\sustain,0.27);
// Pdef(ptn).set(\release,0.92);



(

	//------------------------------------------------------------	
	// how ofter does ~next() get called from engine
	//------------------------------------------------------------	
	~secs = 0.03;

	//------------------------------------------------------------	
	// intial state
	//------------------------------------------------------------	
	~init = { |mo|

		"init ADAM".postln;

		Pdef(ptn).set(\instrument,\adamSynth);
		Pdef(ptn).set(\dur,0.5);
		Pdef(ptn).set(\octave,5);

		Pdef(ptn).set(\attack,0.001);
		Pdef(ptn).set(\sustain,0.27);
		Pdef(ptn).set(\release,0.92);

		// Pdef(ptn).set(\type,\midi);
		// Pdef(ptn).set(\midiout,mo);
		// Pdef(ptn).set(\chan,2);

		//Pdef(ptn).play;

		synth = Synth(\adamSynth);
	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 
		
//		d.rrateMass = ~tween.(d.rrateEvent.sumabs.half.half.half,d.rrateMass,0.2);

		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(2.0)).reciprocal).max(0.125);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1,smooth,0.5);

		// if(smooth < 0.05,{
		// 	Pdef(ptn).stop;
		// 	//Pdef(ptn).set(\dur,0.125*0.5);
		// },{
		// 	if(Pdef(ptn).isPlaying.not,{

		// 		// Pdef(ptn).asStream.next().play();
		// 		Pdef(ptn).play();

		// 	});
		// });

	  	if(Pdef(ptn).isPlaying, {

		 });
				Pdef(ptn).set(\octave,5 + (smooth * 3).floor);
				Pdef(ptn).set(\dur,d.rrateMass);

				synth.set(\freq,200 + (smooth*700));
				synth.set(\amp,smooth*2);

		//Pdef(ptn).set(\attack,(1.0 + d.rrateEvent.sumabs).pow(4).reciprocal);

	 	//Pdef(ptn).set(\dur, Array.geom(5, 1, 2).at( (d.rrateEvent.sumabs.sqrt).floor).reciprocal );

	};

	//------------------------------------------------------------	

	~plot = { |d,p|
		//(smooth * 5).floor.postln;
		//Pdef(ptn).asStream.postln;
		[d.rrateMass,smooth];
	};

	//------------------------------------------------------------	
	// cleanup
	//------------------------------------------------------------	
	~deinit = {

		"deinit ADAM".postln;
		Pdef(ptn).stop;
		synth.free;
		//s.freeAll;
	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = 0;
	~plotMax = 1;

	

)
