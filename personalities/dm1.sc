var m = ~model;
var isOn = false;
var bl = [0,-7,-5].stutter(12);
var cr = [0,-2,-7,-3,0,-2,-7,-3,-5].stutter(5);

m.midiChannel = 6;
m.accelMassAmpThreshold = 0.1;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0],inf),
			\note, Pseq([-5,0,5,7],inf),
			//\func, Pfunc({|e| ~onEvent.(e)}),
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

	// var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(2).floor;

	// Pdef(m.ptn).set(\root,m.com.root);
	// Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 27).reciprocal);
	// Pdef(m.ptn).set(\amp, 0.34 + (m.accelMassFiltered * 0.1));
	// Pdef(m.ptn).set(\octave, 5 + oct);
		
};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 90 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1.0;

~plot = { |d,p|
	d.rrateEvent.x.postln;
	[d.rrateEvent.x];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};
