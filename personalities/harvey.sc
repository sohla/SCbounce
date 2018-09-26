
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});

var smooth = 0;
var moving = false;
var movingB = false;

var midiOut;
var midiChannel = 8;
var notes = [0,12,10,0,0,12,12,10,10,9,7,7,5,2,5];
var note = notes[0];
var threshold = 0.7;
var movement = 0.8;
//------------------------------------------------------------	
//
//------------------------------------------------------------	
Pdef(ptn,
	Pbind(
		\note, Pseq([[-12,0],10,7,2,5,4,-3,-5],inf),
		\args, #[],
		\root, Pseq([0,3,5,2].stutter(16),inf),
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
		Pdef(ptn).set(\octave,4);
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

				midiOut.noteOn(7, 60 + note, 90);



				{
				moving = false;
				midiOut.noteOff(7, 60 +note , 90);
				notes = notes.rotate(-1);
				note = notes[0] ;

				}.defer(2);
			});

			midiOut.control(7, 0, 0 );
		},{

			// if(moving == true,{
			// 	moving = false;
			// 	midiOut.noteOff(2, 60 +note + 5, 90);
			// 	notes = notes.rotate(-1);
			// 	note = notes[0] + 7;
			// });

		});
		Pdef(ptn).set(\dur,(smooth*20).reciprocal);
		Pdef(ptn).set(\amp,movement);

		midiOut.control(midiChannel, 0, (smooth*127).asInteger );

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

		midiOut.allNotesOff(7);
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
		midiOut.control(midiChannel, num, val * 127 );

	};
	

)
