

var smooth = 0;
var moving = false;
var midiOut;
var midiChannel = 5;
var notes = [0,12,-17];
var note = notes[0];
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

		"init EVE".postln;

		midiOut = mo;
	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 

		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(2.0)).reciprocal).max(0.125*0.5);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1,smooth,0.5);

		if(smooth > 0.05,{

			if(moving == false,{
				moving = true;

				midiOut.noteOn(midiChannel, 60 + note -24, 100);
			});

			midiOut.control(midiChannel, 1, (smooth*127).asInteger );
		},{

			if(moving == true,{
				moving = false;
				midiOut.noteOff(midiChannel, 60 + note -24, 100);
				notes = notes.rotate(-1);
				note = notes[0];
			});

		});
	};

	//------------------------------------------------------------	

	~plot = { |d,p|
		[d.rrateMass,smooth];
	};

	//------------------------------------------------------------	
	// cleanup
	//------------------------------------------------------------	
	~deinit = {

		"deinit EVE".postln;
		midiOut.allNotesOff(midiChannel);

	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = 0;
	~plotMax = 1;

	

)
