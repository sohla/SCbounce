var m = ~model;
var cr = [0,-2,3,-4,-5].stutter(5);
m.midiChannel = 2;
m.accelMassAmpThreshold = 0.05;
m.rrateMassThreshold = 0.1;
//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([[-5,0,4]],inf),
			\args, #[],
		);
	);
	Pdef(m.ptn).set(\dur,0.3);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.2);


	Pdef(m.ptn,Pbind( 
		\note, Pseq([[-5,0,4],[-5,0,5],[-5,0,2]],inf),
		//	\octave, Prand([5,6,7,8,9],inf),
	 \strum, 0.09,
	));

	Pdef(m.ptn).play();


};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	var vel = 60;
	var oct = [-48,-36];
	var o = 0;

	if(state == true,{

		cr = cr.rotate(-1);
		m.com.root = cr.[0];
		o = oct.choose;
		m.midiOut.noteOn(10, 60 + m.com.root + o  , vel);
		{m.midiOut.noteOff(10, 60 + m.com.root + o, vel);}.defer(1);
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
	Pdef(m.ptn).set(\amp,0.4);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 14).reciprocal);
	Pdef(m.ptn).set(\strum,0.01+ (m.rrateMassFiltered * 190));
	Pdef(m.ptn).set(\octave, 5 + (m.rrateMassFiltered * 3).floor);
};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 40 );
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





