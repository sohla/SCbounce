var m = ~model;
m.midiChannel = 4;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
	        \degree, Pseq([[0,4,7],[4,7,10],[0,4,7],[4,7,9]], inf),
			\root, Pseq([0,5,-2,3,-4,1,-5].stutter(16),inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
			\octave,Pseq([1,2,3,4,3,2].stutter(3),inf)
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\amp,0.2);
};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
	m.com.root = e.root;
};

~onHit = {|state|

	var vel = 40;
	var oct = [-48,-36,-24,-12].choose;
	var note = 60 + m.com.root + oct + 24;

	if(state == true,{
		m.midiOut.noteOn(6, note, vel);
		{m.midiOut.noteOff(6, note, vel)}.defer(0.1);
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
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 1.2 * m.rrateMassThreshold.reciprocal).reciprocal);

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


