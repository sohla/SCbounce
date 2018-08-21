
// unique name for pattern 
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});

var smooth = 0;
var synth;


//------------------------------------------------------------	
// PATTERN DEF
//------------------------------------------------------------	

Pdef(ptn,
	Pbind(
		\note, Prand([0,2,7],inf),
		\args, #[],
		\amp, Pexprand(0.1,0.4,inf),
		\pan, Pwhite(-0.8,0.8,inf)
));


//------------------------------------------------------------	
//
//------------------------------------------------------------	

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


		Pdef(ptn).set(\type,\midi);
		Pdef(ptn).set(\midiout,mo);
		Pdef(ptn).set(\chan,0);
	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 
		

		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(2.0)).reciprocal).max(0.125*0.5);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1,smooth,0.5);

		if(smooth < 0.05,{
			Pdef(ptn).stop;
			//Pdef(ptn).set(\dur,0.125*0.5);
		},{
			if(Pdef(ptn).isPlaying.not,{

				// Pdef(ptn).asStream.next().play();
				Pdef(ptn).play();

			});
		});

	  	if(Pdef(ptn).isPlaying, {

		 });

		Pdef(ptn).set(\octave,5 + (smooth * 3).floor);
		Pdef(ptn).set(\dur,d.rrateMass);



	};

	//------------------------------------------------------------	

	~plot = { |d,p|
		[d.rrateMass,smooth];
	};

	//------------------------------------------------------------	
	// cleanup
	//------------------------------------------------------------	
	~deinit = {

		"deinit ADAM".postln;
		Pdef(ptn).stop;
	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = 0;
	~plotMax = 1;

	

)
