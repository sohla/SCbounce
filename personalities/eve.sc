


var m = ~model;
var ptn = m.ptn;
m.midiChannel = 9;

//------------------------------------------------------------	
Pdef(ptn,
	Pbind(
		\note, Pseq([0,9,7,0,-3,-5],inf),
		\args, #[],
));
//------------------------------------------------------------	

(

	//------------------------------------------------------------	
	// intial state
	//------------------------------------------------------------	
	~init = ~init <> { 

		Pdef(ptn).set(\dur,0.5);
		Pdef(ptn).set(\octave,3);
		Pdef(ptn).set(\amp,0.8);


		Pdef(ptn).set(\type,\midi);
		Pdef(ptn).set(\midiout,m.midiOut);
		Pdef(ptn).set(\chan,m.midiChannel);
		Pdef(ptn).play();
	};


	~onAmp = {|v|
		// TODO
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
	// do all the work(logic) taking data in and playing pattern/synth
	//------------------------------------------------------------	
	~next = {|d| 

		Pdef(ptn).set(\dur,(m.rrateMassFiltered * 20).reciprocal);
		Pdef(ptn).set(\amp,0.4);

	};

	//------------------------------------------------------------	
	// plot with min and max
	//------------------------------------------------------------	

	~plotMin = -1;
	~plotMax = 1;
	~plot = { |d,p|
		//[amp,threshold,smooth];
		//[(smooth*127).asInteger];
		[m.accelMassFiltered];
	};


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



