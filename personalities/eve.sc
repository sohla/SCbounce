

var ptn = Array.fill(16,{|i|i=90.rrand(65).asAscii});

var notes = [0,0,-5,-2,-2,-8,-10,-10,-12];
var note = notes[0];

var threshold = 0.2;

var isHit = false;
var moving = false;

var amp = 0;
//var ampThreshold = 0.1;



var m = ~model;


m.midiChannel = 9;
//------------------------------------------------------------	
//
//------------------------------------------------------------	
Pdef(ptn,
	Pbind(
		\note, Pseq([0,9,7,0,-3,-5],inf),
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
	~init = { 

		"init EVE".postln;

		Pdef(ptn).set(\dur,0.5);
		Pdef(ptn).set(\octave,3);
		Pdef(ptn).set(\amp,0.8);


		Pdef(ptn).set(\type,\midi);
		Pdef(ptn).set(\midiout,m.midiOut);
		Pdef(ptn).set(\chan,m.midiChannel);
		Pdef(ptn).play();
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
	// process data->model
	//------------------------------------------------------------	
	~processDeviceData = {|d|

		m.accelMass = d.accelEvent.sumabs * 0.33;
		m.rrateMass = d.rrateEvent.sumabs * 0.1;

		m.accelMassFiltered = ~tween.(m.accelMass, m.accelMassFiltered, 0.7);
		m.rrateMassFiltered = ~tween.(m.rrateMass, m.rrateMassFiltered, 0.5);
	};
	//------------------------------------------------------------	
	// triggers
	//------------------------------------------------------------	
	~onHit = {|state|

		var ch = 9;
		var n = [0,2,4,5,7,9,11,12].choose;
		var vel = 50;

		if(state == true,{
			m.midiOut.noteOn(ch, 60 + n, vel);
		},{
			m.midiOut.noteOff(ch, 60 + n, vel);
		});
	};

	~onMoving = {|state|

	};

	//------------------------------------------------------------	
	// process triggers
	//------------------------------------------------------------	
	~processTriggers = {|d|


		
		// isHit

		if(m.accelMassFiltered > 0.2,{
			if(m.isHit == false,{
				m.isHit = true;
				~onHit.(m.isHit);
			});
		},{
			if(m.isHit == true,{
				m.isHit = false;
				~onHit.(m.isHit);
			});
		});

		//isMoving

		if(m.rrateMassFiltered > 0.1,{

			if(m.isMoving == false,{

				m.isMoving = true;
				Pdef(ptn).resume();
			});

			//midiOut.control(midiChannel, 1, (smooth*127).asInteger );
		},{

			if(m.isMoving == true,{

				m.isMoving = false;
				Pdef(ptn).pause();
			});
		});			
	};
	//------------------------------------------------------------	
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 




		// amp = (d.ampValue * 8).clip2(1.0);//~tween.(v,amp,0.9);
		// vel = amp * 120;

		// if(vel < 10,{vel = 10});


		//

		
		///
		Pdef(ptn).set(\dur,(m.rrateMassFiltered * 20).reciprocal);
		Pdef(ptn).set(\amp,0.4);
		
		///
	////

		// if( amp > threshold,{
		// 	if(isHit == false,{
		// 		{m.midiOut.noteOn(ch, 60 + n, vel)}.defer;
		// 		{m.midiOut.noteOff(ch, 60 + n, vel)}.defer(0.5);
		// 		["hit",threshold,amp].postln;
		// 		isHit = true;
		// 	});
		// },{
		// 	if(isHit == true,{
		// 		"OFF".postln;
		// 		isHit = false;
		// 	});
		// });

	};

	//------------------------------------------------------------	

	~plot = { |d,p|
		//[amp,threshold,smooth];
		//[(smooth*127).asInteger];
		[m.accelMassFiltered];
	};

	//------------------------------------------------------------	
	// cleanup
	//------------------------------------------------------------	
	~deinit = {
		Pdef(ptn).stop();

		"deinit EVE".postln;
		m.midiOut.allNotesOff(m.midiChannel);

	};

	//------------------------------------------------------------	
	// min and max of plotters output
	//------------------------------------------------------------	

	~plotMin = -1;
	~plotMax = 1;

	//------------------------------------------------------------	
	// midi control
	//------------------------------------------------------------	
	~midiControllerValue = {|num,val|

		//[num,val].postln;

		// if(num == 4,{ threshold = 0.01 + (val * 0.99)});

		// threshold = threshold * 2;
		// midiOut.control(m.midiChannel, num, val * 127 );

	};
	

)



