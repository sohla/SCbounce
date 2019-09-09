var m = ~model;
m.midiChannel = 4;
m.accelMassAmpThreshold = 0.4;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
	        \degree, Pseq([[0,4,7],[4,7,10],[0,4,7],[4,7,9]], inf),
			\args, #[],
			\octave,Pseq([1,2,3,4,5,4,3,2].stutter(3),inf)
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	var vel = 50;
	var note = 0;
	if(state == true,{
		m.midiOut.noteOn(m.midiChannel, 60 + m.com.root + note - 48 - 12 , vel);
		{m.midiOut.noteOff(m.midiChannel, 60 + m.com.root + note - 48 - 12, vel);}.defer(0.3);
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

	//Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\dur,(m.rrateMassFiltered * 20).reciprocal);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.rrateMassFiltered * 127 );
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


