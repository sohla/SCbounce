
// unique name for pattern 
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});
var midiOut;
var midiChannel = 0;

var roots = 0!12;
// use to hear this synth onces
//x = Synth(\harpsichord1);s.sendBundle(0.5,[\n_set,x.nodeID,\gate,0]);

//------------------------------------------------------------	
// PATTERN DEF
//------------------------------------------------------------	

Pdef(ptn,
	Pbind(
        \degree, Pseq([0,4,7,9], inf),
		\args, #[],
		\octave,Pseq(([1,2,3,4,3,2]+2).stutter(3),inf),
		\amp, Pexprand(0.1,0.4,inf),
		\pan, Pwhite(-0.8,0.8,inf),
		\root, Pseq([0,4,7,10,5,8].stutter(30),inf),
		\strum, 0.1
));


(

	//------------------------------------------------------------	
	// how ofter does ~next() get called from engine
	//------------------------------------------------------------	
	~secs = 0.03;

	//------------------------------------------------------------	
	// intial state
	//------------------------------------------------------------	
	~init = { |mo|

		"init SUSAN".postln;

		Pdef(ptn).set(\instrument,\harpsichord1);
		Pdef(ptn).set(\dur,1.0);

		Pdef(ptn).set(\type,\midi);
		Pdef(ptn).set(\midiout,mo);
		Pdef(ptn).set(\chan,midiChannel);

		midiOut = mo;

	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 
		
		var val = 0;


		d.rrateMass = ~tween.(d.rrateEvent.sumabs.half / pi,d.rrateMass,0.3);

		if(d.rrateMass < 0.07,{
			Pdef(ptn).pause;
		},{
			if(Pdef(ptn).isPlaying.not,{Pdef(ptn).play});

			midiOut.control(midiChannel, 0, 127.0-(d.rrateMass*127).asInteger );
		});
		

		Pdef(ptn).set(\dur,(d.rrateMass *20).reciprocal);


	};

	//------------------------------------------------------------	
	// cleanup
	//------------------------------------------------------------	
	~deinit = {

		"deinit SUSAN".postln;
		Pdef(ptn).stop;

		midiOut.allNotesOff(midiChannel);
	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = 0;
	~plotMax = 0.1;

	//------------------------------------------------------------	
	// utility for output to a plotter : returns a value that
	// that will be put at the end of plotters data array
	//------------------------------------------------------------	

	~plot = { |d,p|
		(1.0-(d.rrateEvent.sumabs / 3.0 / pi.twice)) * 0.1;
	};
	
	//------------------------------------------------------------	
	// midi control
	//------------------------------------------------------------	
	~midiControllerValue = {|num,val|
		//[num,val].postln;

		//midiOut.control(midiChannel, num, val * 127 );

	};

)

