
// unique name for pattern 
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});
var midiOut;
var midiChannel = 4;

//------------------------------------------------------------	
// SYNTH DEF
//------------------------------------------------------------	

(
SynthDef(\harpsichord1, { arg out = 0, freq = 440, amp = 0.1, pan = 0, rls = 0.5, width = 0.5;
    var env, snd;
	env = Env.perc(releaseTime:rls,level: amp).kr(doneAction: 2);
	snd = Pulse.ar(freq, width, 0.75);
	snd = snd * env;
	Out.ar(out, Pan2.ar(snd, pan));
}).add;
);

// use to hear this synth onces
//x = Synth(\harpsichord1);s.sendBundle(0.5,[\n_set,x.nodeID,\gate,0]);

//------------------------------------------------------------	
// PATTERN DEF
//------------------------------------------------------------	

Pdef(ptn,
	Pbind(
//         \degree, Pseq([[0,4,8],[3,8,11],[2,5,9],[0,5,12],[2,7,10]], inf),
        \degree, Pseq([[0,4,7],[4,7,10],[-4,0,3]], inf),
		\args, #[],
		\octave,Pseq(#[1,1,1,2,2,2,3,3,3,4,4,4,5,5,5,6,6,6,5,5,5,4,4,4,3,3,3,2,2,2],inf),
		//\dur,Pseq(#[1.0,0.5,0.5],inf),
		\amp, Pexprand(0.1,0.4,inf),
		\pan, Pwhite(-0.8,0.8,inf)
		//\strum, 0.06
));

//use this to test patter/synth with default gui
// Pdef(ptn).play.gui;
// Pdef(ptn).set(\instrument,\harpsichord1);
// Pdef(ptn).set(\dur,0.25);
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

		"init SUSAN".postln;

		Pdef(ptn).set(\instrument,\harpsichord1);
		Pdef(ptn).set(\dur,1.0);
//		Pdef(ptn).set(\octave,Pseq(#[1,2,3,4,5,6],inf));

		Pdef(ptn).set(\type,\midi);
		Pdef(ptn).set(\midiout,mo);
		Pdef(ptn).set(\chan,midiChannel);

		Pdef(ptn).play;

		midiOut = mo;

	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 
		
		var val = 0;


		d.rrateMass = ~tween.(d.rrateEvent.sumabs.half / pi,d.rrateMass,0.3);

		if(d.rrateMass < 0.09,{
			Pdef(ptn).pause;
		},{
			if(Pdef(ptn).isPlaying.not,{Pdef(ptn).resume});

		});
		

		Pdef(ptn).set(\dur,(d.rrateMass *25).reciprocal);

		midiOut.control(midiChannel, 0, (d.rrateMass*127).asInteger );
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
		[num,val].postln;

				midiOut.control(midiChannel, num, val * 127 );

	};

)

