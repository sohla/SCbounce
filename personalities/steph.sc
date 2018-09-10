

var smooth = 0;
var moving = false;
var midiOut;
var midiChannel = 2;
var notes = [0,-12];
var note = notes[0];
var isHit = false;
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

		"init STEPH".postln;

		midiOut = mo;
	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 
		
		d.accelMass = d.accelEvent.sumabs * 0.1;
		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(2.0)).reciprocal).max(0.125*0.5);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1,smooth,0.5);

		if(d.accelMass > 0.05,{

			if(isHit == false,{
				var n = [0,2,5,4].choose + 24;
				midiOut.noteOn(1, 60+note+n, 120);
				{midiOut.noteOff(1, 60+note+n, 120)}.defer(0.5);

				isHit = true;

			});

		},{
			isHit = false;
		});


		if(smooth > 0.05,{

			if(moving == false,{
				moving = true;

				midiOut.noteOn(midiChannel, 60 + note -12, 100);
			});

			midiOut.control(midiChannel, 1, (smooth*127).asInteger );
		},{

			if(moving == true,{
				moving = false;
				midiOut.noteOff(midiChannel, 60 + note -12, 100);
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

		"deinit STEPH".postln;
		midiOut.allNotesOff(midiChannel);

	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = 0;
	~plotMax = 1;

	

)
