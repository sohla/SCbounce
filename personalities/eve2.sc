var m = ~model;
var isOn = false;
// var bl = [0,-7,-3,-10,-8,-7,-3,-5].stutter(2);
var bl = [0,-7,-5].stutter(12);
var cr = [0,5,-2,3,-4,1,-5];

m.midiChannel = 6;
m.accelMassAmpThreshold = 0.4;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0],inf),
			\note, Pseq([-5,0,5,7],inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.8);


};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	if(state == true,{
		m.midiOut.noteOn(m.midiChannel, 60-24  + m.com.root, 100);
		{m.midiOut.noteOff(m.midiChannel, 60-24  + m.com.root, 0)}.defer(1);

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

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(2).floor;

	Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\dur,(m.rrateMassFiltered * 7).reciprocal);
	Pdef(m.ptn).set(\amp, 0.2);
	Pdef(m.ptn).set(\octave, 5 + oct);
	
};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.rrateMassFiltered * 127 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 2;

~plot = { |d,p|
	[m.accelMassAmp];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};
