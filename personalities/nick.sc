

var smooth = 0;
var moving = false;
var midiOut;
var midiChannel = 1;
var notes = [0,2,7,9,11,14];
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

		"init NICK".postln;

		midiOut = mo;
	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 

		d.accelMass = d.accelEvent.sumabs * 0.1;//~tween.(d.accelEvent.sumabs * 0.1,d.accelMass,0.9);
		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(2.0)).reciprocal).max(0.125*0.5);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1,smooth,0.5);

		if(d.accelMass > 0.03,{
			if(moving == false,{
				moving = true;
			"YES".postln;

				midiOut.noteOn(midiChannel, 60 + note + 24, 100);
			});

			midiOut.control(midiChannel, 1, (smooth*80).asInteger );
		},{

			if(moving == true,{
				"NO".postln;
				moving = false;
				midiOut.noteOff(midiChannel, 60 + note + 24, 100);
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

		"deinit NICK".postln;
		midiOut.allNotesOff(midiChannel);

	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = 0;
	~plotMax = 1;

	

)
