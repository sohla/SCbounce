var m = ~model;
var isOn = false;
var root;
var notes = [0,12,10,0,0,12,12,10,10,9,7,7,5,2,5];
var note = notes[0];


m.midiChannel = 8;



//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([[-12,0],10,7,2,5,4,-3,-5],inf),
			\root, Pseq([0,5,-2,3,-4,1,-5].stutter(16),inf),
			// \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.6);
	Pdef(m.ptn).set(\octave,4);
	Pdef(m.ptn).set(\amp,0.6);
	Pdef(m.ptn).play();

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
	//root = e.root;
};

~onHit = {|state|
	// var oct = [0,12,24,36];
	// var ot = oct.choose;

	// if(state == true,{
	// 	m.midiOut.noteOn(2, 60 + root + ot  , 50);
	// 	{m.midiOut.noteOff(2, 60 + root + ot, 0)}.defer(0.2);

	// },{
	// });

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
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 2 * m.rrateMassThreshold.reciprocal).reciprocal);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 5, m.rrateMassFiltered * 127 );
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
};
