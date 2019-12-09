var m = ~model;
var isOn = false;
var bl = [0,-2,5].stutter(12);
var cr = [0,5,-2,3,-4,1,-5];

m.midiChannel = 9;
m.accelMassAmpThreshold = 0.1;
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
	Pdef(m.ptn).set(\amp,0.8);

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq([-5,0,5,7,4,9,2,-2],inf)
	));

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	var oo = [36,48,12,24];

	if(state == true,{
		m.com.root = bl.[0];
		cr = cr.rotate(-1);
		m.midiOut.noteOn(m.midiChannel + 1, 60-oo.choose  + m.com.root, 40);
		{m.midiOut.noteOff(m.midiChannel + 1, 60-oo.choose  + m.com.root, 0)}.defer(0.5);
		bl = bl.rotate(-1);
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

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(3).floor;

	Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 6).reciprocal);
	Pdef(m.ptn).set(\amp, 0.4);
	Pdef(m.ptn).set(\octave, 5 + oct);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 127 );
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
