var m = ~model;
var d = ~device;

var isOn = false;
var bl = [0,-7,-5].stutter(12);
var cr = [0,-2,-7,-3,0,-2,-7,-3,-5].stutter(5);

var rateValue = 0, av = 0;

var note = 60;
m.midiChannel = 2;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,2,5,4,12,10,7,5,10,7,4,2,7].stutter(4),inf),
			\octave,Pseq(([0,1,2,1]+3),inf),
			\args, #[],
		);
	);


	Pdef(m.ptn).set(\dur,1);
	Pdef(m.ptn).set(\root,0);

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
	// av = (m.accelMassFiltered - 3.48) / 12.0;
	// av = av.clip(0.0, 1.0);
	rateValue = (1 + 2.pow((m.rrateMassFiltered * 4).round)-1).reciprocal ;


	Pdef(m.ptn).set(\root, m.rrateMassFiltered.round * 24);



	Pdef(m.ptn).set(\dur, rateValue * 1);

	if(m.rrateMassFiltered > 0.1	, {
		Pdef(m.ptn).set(\amp,0.7);
	},{
		Pdef(m.ptn).set(\amp,0.0);
	});


};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.rrateMassFiltered * 127 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|

	[ (rateValue) ,m.rrateMassFiltered];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};

