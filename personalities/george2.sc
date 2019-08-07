var m = ~model;
m.midiChannel = 7;
m.accelMassThreshold = 0.3;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
	        \degree, Pseq([0,2,5,4,7,7,9,12,11], inf),
			\args, #[],
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

	var vel = 10;
	var note = [0,4,7,9,7,4,2].choose;
	if(state == true,{
		m.midiOut.noteOn(m.midiChannel, 60 + m.com.root + note + 24, vel);
		{m.midiOut.noteOff(m.midiChannel, 60 + m.com.root + note + 24, vel);}.defer(0.3);
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

~onAmp = {|v|
	// TODO
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\octave, 4 + (m.rrateMassFiltered * 3).floor);
	Pdef(m.ptn).set(\dur,(m.rrateMassFiltered * 20).reciprocal);

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


