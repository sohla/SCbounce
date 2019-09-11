var m = ~model;
var isOn = false;
var cr = [0,2,4,5,7,9];

m.midiChannel = 1;
m.accelMassAmpThreshold = 0.1;
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

	Pdef(m.ptn).set(\dur,0.18);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.9);

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq([0,1,2,3,4,5,6,7],inf),
		// \dur, Pseq([0.18].stutter(6).add(0.36),inf),
		// \func, Pfunc({|e| ~onEvent.(e)}),
	));
	Pdef(m.ptn).play();
};
//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
	// e.postln;
};

~onHit = {|state|

	if(state == true,{
		Pdef(m.ptn).set(\chan, m.midiChannel + 1);
		{Pdef(m.ptn).set(\chan, m.midiChannel)}.defer(0.3);
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
~next = {|d| 

	var oct = 1 + ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(1).floor;
	var div = [(oct.reciprocal)].stutter(oct.asInteger) * 0.18;

	// Pdef(m.ptn,Pbind( 
	// 	\note, Pseq([0,1,2,3,4,5,6,7],inf),
	// 	\dur, Pseq(div,inf),
	// 	// \func, Pfunc({|e| ~onEvent.(e)}),
	// ));
	//div.class.postln;
	// Pdef(m.ptn).set(\dur, m.com.dur * 0.125);
	// Pdef(m.ptn).set(\root,m.com.root);
	//Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 6).reciprocal);
	//Pdef(m.ptn).set(\octave, 3 + oct);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 1, m.accelMassFiltered * 127 );
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
