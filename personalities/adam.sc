
// unique name for pattern 
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});

var smooth = 0;
var synth;

var notes = [0,5,8,3];
var note = notes[0];

var moving = false;
var midiOut;
var midiChannel = 0;

//------------------------------------------------------------	
// PATTERN DEF
//------------------------------------------------------------	

Pdef(ptn,
	Pbind(
		\note, Prand([0,2,[4,7]],inf),
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
		Pdef(ptn).set(\chan,midiChannel);

		midiOut = mo;
	


	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 
		
		d.accelMass = d.accelEvent.sumabs * 0.1;
		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(1.0)).reciprocal).max(0.125*0.5);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1,smooth,0.5);

		if(smooth > 0.05,{

			if(moving == false,{
				moving = true;

				midiOut.noteOff(midiChannel, 60 + note -12, 70);
				midiOut.noteOn(3, 60 + note -24, 100);
				Pdef(ptn).play();
			});

			midiOut.control(midiChannel, 1, (smooth*127).asInteger );
		},{

			if(moving == true,{
				moving = false;
				Pdef(ptn).stop;
				midiOut.noteOn(midiChannel, 60 + note -12, 70);
				midiOut.noteOff(3, 60 + note -24, 100);
				notes = notes.rotate(-1);
				note = notes[0];
			});

		});


		if(d.accelMass > 0.2,{
				midiOut.noteOn(midiChannel, 60 + note - 24, 100);
		},{

			});

		Pdef(ptn).set(\octave,4 + (smooth * 2).floor);
		Pdef(ptn).set(\root,note);
		Pdef(ptn).set(\dur,0.2);

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

		midiOut.allNotesOff(midiChannel);
		midiOut.allNotesOff(3);
	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = 0;
	~plotMax = 1;

	
	//------------------------------------------------------------	
	// midi control
	//------------------------------------------------------------	
	~midiControllerValue = {|num,val|
		[num,val].postln;
		midiOut.control(midiChannel, num, val * 127 );
	};


)
