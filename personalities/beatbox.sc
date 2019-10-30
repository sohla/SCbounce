	-var isOn = false;
var cr = [0,2,4,5,7,9];
var selector = 1;

m.midiChannel = 10;
m.accelMassAmpThreshold = 0.5;
m.rrateMassThreshold = 0.4;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,	
		Pbind(
			\note, Pseq([0],inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.36);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.9);

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq([0,2,4,5,7,9],inf)
	));
	Pdef(m.ptn).play();
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	if(state == true,{
		cr = cr.rotate(-1);
		// m.midiOut.noteOn(3, 60  + cr[0], 100);
		// {m.midiOut.noteOff(3 + 1, 60  + cr[0], 0)}.defer(0.5);
	},{
	});
};

~onMoving = {|state|

	if(state == true,{
		Pdef(m.ptn).set(\amp, 0.9);
	},{
		Pdef(m.ptn).set(\amp, 0.0);
	});
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	

//Pswitch
// Pindex
~next = {|d| 

	selector = (m.accelMassFiltered * 4).floor;

	if( selector > 5, {
		Pdef(m.ptn).set(\root,4);
	},{
		Pdef(m.ptn).set(\root,0);
	});

	// Pdef(m.ptn).set(\root,m.com.root);
	//Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 6).reciprocal);
	//Pdef(m.ptn).set(\octave, 3 + oct);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 3,64 + (m.com.root * 10));
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|

	[m.rrateMassFiltered];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

	//[num,val].postln;

	// if(num == 4,{ threshold = 0.01 + (val * 0.99)});

	// threshold = threshold * 2;
	//m.midiOut.control(m.midiChannel, 65, val * 127 );

};

