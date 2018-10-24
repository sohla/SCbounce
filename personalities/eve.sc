

var smooth = 0;
var moving = false;
var midiOut;
var midiChannel = 5;
var notes = [0,0,-5,-2,-2,-8,-10,-10,-12];
var note = notes[0];

var threshold = 0.7;
var isHit = false;

var amp = 0;
//var ampThreshold = 0.1;

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


	~onAmp = {|v|

		// var ch = 9;
		// var n = [0,2,4,5,7,9,11,12].choose;
		// amp = v * 2;//~tween.(v,amp,0.9);

		// if( amp > threshold,{
		// 	if(isHit == false,{
		// 		{midiOut.noteOn(ch, 60 + n, amp * 512)}.defer(0.01);
		// 		["hit",threshold,amp].postln;
		// 		isHit = true;
		// 	});
		// },{
		// 	if(isHit == true,{
		// 	"OFF".postln;
		// 		{midiOut.noteOff(ch, 60 + n, 0)}.defer(0.3);
		// 	});
		// 	isHit = false;
		// });

	};
	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 


		var ch = 9;
		var n = [0,2,4,5,7,9,11,12].choose;
		amp = d.ampValue * 2;//~tween.(v,amp,0.9);

		d.accelMass = d.accelEvent.sumabs * 0.1;
		d.rrateMass = (2.pow(d.rrateEvent.sumabs.div(2.0)).reciprocal).max(0.125*0.5);
		smooth = ~tween.(d.rrateEvent.sumabs * 0.1,smooth,0.5);
		

		////

		if( amp > threshold,{
			if(isHit == false,{
				{midiOut.noteOn(ch, 60 + n, amp * 512)}.defer(0.03);
				["hit",threshold,amp].postln;
				isHit = true;
			});
		},{
			if(isHit == true,{
				"OFF".postln;
				{midiOut.noteOff(ch, 60 + n, 0)}.defer(0.1);
			});
			isHit = false;
		});

		/////
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
		[amp,threshold];
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

		if(num == 4,{ threshold = 0.01 + (val * 0.99)});

		threshold = threshold * 2;
		// midiOut.control(midiChannel, num, val * 127 );

	};
	

)
