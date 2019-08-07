var m = ~model;
var notes = [[-12,0],7,10,[-12,0],7,12,[-8,4],7,10];
m.midiChannel = 0;
m.accelMassThreshold = 0.3;
m.rrateMassThreshold = 0.1;

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

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,5);
	// Pdef(m.ptn).set(\amp,Pexprand(0.3,0.6,inf));

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq(notes,inf)
	));

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	var vel = 10;
	var note = notes.flat.choose;
	if(state == true,{
		note.postln;

		m.midiOut.noteOn(m.midiChannel, 60 + m.com.root + note + 24 , vel);
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

	Pdef(m.ptn).set(\octave,3 + (m.rrateMassFiltered * 4).floor);
	Pdef(m.ptn).set(\dur, (0.5 - (m.rrateMassFiltered * 0.26)));
	Pdef(m.ptn).set(\amp, 1 - (m.rrateMassFiltered.pow(3)*2) * 0.4);

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

