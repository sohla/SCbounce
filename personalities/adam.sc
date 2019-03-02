
// unique name for pattern 
var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});

var smooth = 0;
var synth;

var notes = [0,5,8,3];
var note = notes[0];

var moving = false;
var midiOut;
var midiChannel = 0;

var threshold = 0.7;
var isHit = false;

var trigCount = 0;

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

		if(d.accelMass > threshold,{

			if(isHit == false,{
				var n = [0.2,7,12,19].choose ;
				// midiOut.control(midiChannel, 2, 0 );
				midiOut.noteOn(midiChannel, 60+12+n+note, 10);
				{midiOut.noteOff(midiChannel, 60+12+n+note, 0)}.defer(0.04);

				isHit = true;

			});

		},{
			isHit = false;
		});

		if(smooth > 0.11,{

			if(moving == false,{
				moving = true;

				//midiOut.noteOn(3, 60 + note -24, 100);
				Pdef(ptn).play();
			});

			midiOut.control(midiChannel, 0, (smooth*127).asInteger );
		},{

			if(moving == true,{
				moving = false;
				Pdef(ptn).stop;
				midiOut.noteOff(3, 60 + note - 24, 90);
				notes = notes.rotate(-1);
				note = notes[0];
				Pdef(ptn).set(\root,note);
				midiOut.noteOn(3, 60 + note -24, 90);
			});

		});

		if(d.ampValue * 20.0 > 0.1 && trigCount == 0, {
			trigCount = 5;

			midiOut.noteOn(15, 30 + 50.rand, 110);
		});

		trigCount = trigCount - 1;

		if (trigCount < 0, {trigCount = 0});


		Pdef(ptn).set(\octave,5 + (smooth * 3).floor);
		Pdef(ptn).set(\dur, (0.4- (smooth * 0.22)));

	};

	//------------------------------------------------------------	

	~plot = { |d,p|
		[d.ampValue * 10.0];
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

		if(num == 4,{ threshold = 0.005 + (val * 0.7)});

		//midiOut.control(4, num, val * 127 );
	};


)



