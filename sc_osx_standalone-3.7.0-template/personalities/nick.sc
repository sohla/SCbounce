
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});

var smooth = 0;
var moving = false;
var movingB = false;

var midiOut;
var midiChannel = 1;
var notes = [12,11,7,0,12,11,7,5,12,11,7,2,12,11,7,0];
var note = notes[0];
var threshold = 0.7;
var movement = 0.8;
//------------------------------------------------------------	
//
//------------------------------------------------------------	
Pdef(ptn,
	Pbind(
		\note, Prand([0,2,7,9,11,14],inf),
		\args, #[],
		// \amp, 0.8,
		//\pan, Pwhite(-0.8,0.8,inf)
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

		"init NICK".postln;

		Pdef(ptn).set(\dur,0.5);
		Pdef(ptn).set(\octave,6);
		Pdef(ptn).set(\amp,0.8);


		Pdef(ptn).set(\type,\midi);
		Pdef(ptn).set(\midiout,mo);
		Pdef(ptn).set(\chan,midiChannel);

		midiOut = mo;
	};

	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 

		d.accelMass = d.accelEvent.sumabs * 0.1;//~tween.(d.accelEvent.sumabs * 0.1,d.accelMass,0.9);
		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(2.0)).reciprocal).max(0.125*0.5);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1 ,smooth,0.5);

		if(d.accelMass > threshold	,{
			if(moving == false,{
				moving = true;

				midiOut.control(0, 0, 10 );
				midiOut.noteOn(0, 60 - 24 + note, 60);
			});

		},{

			if(moving == true,{
				moving = false;
				midiOut.noteOff(0, 60 - 24 + note, 90);
				notes = notes.rotate(-1);
				note = notes[0] + 7;
			});

		});
		Pdef(ptn).set(\dur,(smooth*20).reciprocal);
		Pdef(ptn).set(\amp,movement);

		midiOut.control(midiChannel, 0, (smooth*175).asInteger );

		if(smooth > 0.1,{

			if(movingB == false,{
				movingB = true;

				Pdef(ptn).play();
			});

			//midiOut.control(midiChannel, 1, (smooth*127).asInteger );
		},{

			if(movingB == true,{
				movingB = false;

				Pdef(ptn).stop();
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
		Pdef(ptn).stop();
		"deinit NICK".postln;
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
		// if(num == 1,{ movement = val * 0.8});
//		midiOut.control(midiChannel, num, val * 127 );

	};
	

)
