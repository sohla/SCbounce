var m = ~model;
// var cr = [0,5,-2,3,-4,1,-5].stutter(4);
var cr = [0,-2,3].stutter(5);
m.midiChannel = 1;
m.accelMassAmpThreshold = 0.4;
m.rrateMassThreshold = 0.1;
//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([[-5,0,4]],inf),
			// \strum, 0.22,
			\args, #[],
		);
	);
	Pdef(m.ptn).set(\dur,0.3);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.2);


	Pdef(m.ptn,Pbind( 
		\note, Pseq([[-5,0,4],[-5,0,5],[-5,0,2]],inf),
		// \strum, 0.12,
	));

	Pdef(m.ptn).play();


};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	var vel = 50;

	if(state == true,{

		m.com.root = cr.[0];
		m.midiOut.noteOn(10, 60 + m.com.root - 36 , vel);
		{m.midiOut.noteOff(10, 60 + m.com.root - 36, vel);}.defer(1);
		cr = cr.rotate(-1);
	},{
	});
};

~onMoving = {|state|

	if(state == true,{
		Pdef(m.ptn).resume();
	},{
		Pdef(m.ptn).pause();
	});
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 14).reciprocal);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 120 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
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





