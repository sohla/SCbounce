var m = ~model;
var d = ~device;

var isOn = false;
var bl = [0,-7,-5].stutter(12);
var cr = [0,-2,-7,-3,0,-2,-7,-3,-5].stutter(5);

var rateValue = 0;

var note = 60;
m.midiChannel = 1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,3,4,8,0,9,5,14],inf),
			\octave,Pseq(([0]+3),inf),
			\root, Pseq([0].stutter(4),inf),
			\args, #[],
		);
	);


	Pdef(m.ptn).set(\dur,0.25);
	Pdef(m.ptn).set(\amp,0.7);
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

	// rateValue = (1.0 + (m.rrateMassFiltered * 4)).reciprocal * 0.5;
	rateValue = (1 + 2.pow((m.rrateMassFiltered * 3).round)-1).reciprocal ;


	Pdef(m.ptn).set(\dur, rateValue);

	if(m.rrateMassFiltered > 0.1	, {
		Pdef(m.ptn).set(\amp,0.7);
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


2.pow(3)