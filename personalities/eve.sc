

var smooth = 0;
var moving = false;
var midiOut;
var midiChannel = 5;
var notes = [0,0,-5,-2,-2,-8,-10,-10,-12];
var note = notes[0];

var threshold = 0.7;
var isHit = false;

var amp = 0;
var ampThreshold = 0.1;

//------------------------------------------------------------	
//
//------------------------------------------------------------	

(

	//------------------------------------------------------------	
	// how ofter does ~next() get called from engine
	//------------------------------------------------------------	
	~secs = 0.01;

	//------------------------------------------------------------	
	// intial state
	//------------------------------------------------------------	
	~init = { |mo|

		"init EVE".postln;

		midiOut = mo;
	};


	~onAmp = {|v|

		var ch = 9;

		amp = ~tween.(v / 100,amp,0.9);

		if( amp > ampThreshold,{
		//if(d.accelMass > threshold,{

			if(isHit == false,{
				var n = [0,2,4,5,7,9,11,12].choose ;
				// midiOut.control(midiChannel, 2, 0 );
				midiOut.noteOn(ch, 60 + n, (v / 1024) * 512);
				{midiOut.noteOff(ch, 60 + n, 0)}.defer(0.04);

				isHit = true;

			});

		},{
			isHit = false;
		});

	};
	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 

		d.accelMass = d.accelEvent.sumabs * 0.1;
		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(2.0)).reciprocal).max(0.125*0.5);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1,smooth,0.5);
		



		if(smooth > 0.05,{

			if(moving == false,{
				moving = true;

				midiOut.noteOn(midiChannel, 60 + note -24, 100);
			});

			midiOut.control(midiChannel, 0, (smooth*127).asInteger );
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
		[amp];
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

	//------------------------------------------------------------	
	// midi control
	//------------------------------------------------------------	
	~midiControllerValue = {|num,val|

		//[num,val].postln;

		if(num == 4,{ threshold = 0.005 + (val * 0.7)});
		// midiOut.control(midiChannel, num, val * 127 );

	};
	

)
