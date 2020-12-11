var m = ~model;
var d = ~device;

var isOn = false;
var bl = [0,-7,-5].stutter(12);
var cr = [0,-2,-7,-3,0,-2,-7,-3,-5].stutter(5);

var rateValue = 0;

var note = 60;
m.midiChannel = 0;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,5,10,9,7,12,9,5],inf),
			\octave,Pseq(([0,-1,1,1,2,1,1,1,2,2,1,1,2,2,1,1]+3),inf),
			\root, Pseq([0,2,5,3,-2].stutter(24),inf),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\amp,0.2);
	Pdef(m.ptn).play();

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|


};

~onMoving = {|state|
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 
	// m.accelMassAmp = (d.blob.center.x / 450.0) + (d.blob.center.y / 450.0);

	rateValue = (1.0 + (m.rrateMassFiltered * 8)).reciprocal;


	Pdef(m.ptn).set(\dur, rateValue);

	if(rateValue < 0.8	, {
		Pdef(m.ptn).set(\amp,0.2);
	},{
		Pdef(m.ptn).set(\amp,0.0);
	});


};

~nextMidiOut = {|d|

};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|

	[ (rateValue) , m.rrateMassFiltered];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};


