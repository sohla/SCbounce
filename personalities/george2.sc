var m = ~model;
var bl = [0,4,-2,2].stutter(8);
var cr = [0,5,-2,3,-4,1,-5];
var isOn = false;

m.midiChannel = 7;
m.accelMassAmpThreshold = 0.4;
m.rrateMassThreshold = 0.2;

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

	var vel = 80;
	var oo = [24,36];

	if(state == true,{
		m.com.root = bl.[0];
		cr = cr.rotate(-1);
		m.midiOut.noteOn(10, 60-oo.choose  + m.com.root, vel);
	},{
		m.midiOut.noteOff(10, 60-oo.choose  + m.com.root, 0);
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


	Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\octave, 5 + (m.rrateMassFiltered * 3).floor);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 12).reciprocal);

};

~nextMidiOut = {|d|
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|

	[m.rrateMassFiltered.pow(3)*2, m.accelMassFiltered, m.com.rrateMass];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};


