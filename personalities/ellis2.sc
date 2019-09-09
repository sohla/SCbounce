var m = ~model;
var root = 0;
m.midiChannel = 0;
m.accelMassAmpThreshold = 0.4;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,7,10,0,7,12,4,7,10],inf),
			\root, Pseq([0,5,-2,3,-4,1,7].stutter(12),inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,5);
	// Pdef(m.ptn).set(\amp,Pexprand(0.3,0.6,inf));


};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
	root = e.root;
	root.postln;
};

~onHit = {|state|

	var vel = 110;
	if(state == true,{
		m.midiOut.noteOn(m.midiChannel, 60 + root - 24 , vel);
	},{
		m.midiOut.noteOff(m.midiChannel, 60 + root - 24, 0);
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

	Pdef(m.ptn).set(\octave,4 + (m.rrateMassFiltered * 3).floor);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 6).reciprocal);

};

~nextMidiOut = {|d|
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	// [(m.rrateMassFiltered * 3).ceil.mod(3)];
	[m.rrateMassFiltered.pow(3)*2, m.accelMassFiltered, m.com.rrateMass];
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

