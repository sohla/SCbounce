var m = ~model;
var isOn = false;
var bl = [0,-7,-5].stutter(12);
var cr = [0,-2,-7,-3,0,-2,-7,-3,-5].stutter(5);

m.midiChannel = 6;
// m.accelMassAmpThreshold = 1.4;
// m.rrateMassThreshold = 0.1;

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

	var oc = [12,24];
	var note, occ;

	if(state == true,{
		cr = cr.rotate(-1);
		
		occ = oc.choose;
		note = 60  - oc.choose + m.com.root + cr[0];
		m.midiOut.noteOn(m.midiChannel + 3, note, 100);
		{m.midiOut.noteOff(m.midiChannel + 3, note, 0)}.defer(0.8);
		
		note = 60  - 24 - occ + m.com.root + cr[0];
		m.midiOut.noteOn(m.midiChannel + 4, note, 60);
		{m.midiOut.noteOff(m.midiChannel + 4, note, 0)}.defer(0.8);

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
// ~processTriggers = {|d|

// 	var changeState = {|state|
// 		if(~model.isHit != state,{
// 			~model.isHit = state;
// 			if(~model.isHit == true,{
// 				//"Note ON".postln;
// 				~onHit.(~model.isHit);
// 			},{
// 				//"Note OFF".postln;
// 				~onHit.(~model.isHit);
// 			});
// 		});
// 	};

// 	//d.ampValue = d.ampValue  * 10;
// 	"overload".postln;
// 	// should we tweak this constants!?
// 	if( d.ampValue > 0.08,{
// 		// if( ~model.accelMass > 0.2,{ 
// 			~model.accelMassAmp = d.ampValue;
// 		},{
// 			~model.accelMassAmp = 0.0;
// 		});
// 	// });

// 	if(~model.accelMassAmp > ~model.accelMassAmpThreshold,{
// 		changeState.(true);
// 	},{
// 		changeState.(false);
// 	});


// 	//isMoving

// 	if(~model.rrateMassFiltered > ~model.rrateMassThreshold,{

// 		if(~model.isMoving == false,{
// 			~model.isMoving = true;
// 			~onMoving.(~model.isMoving);
// 			//Pdef(~model.ptn).resume();
// 		});

// 		//midiOut.control(midiChannel, 1, (smooth*127).asInteger );
// 	},{

// 		if(~model.isMoving == true,{
// 			~model.isMoving = false;
// 			~onMoving.(~model.isMoving);
// 		});
// 	});			
// };

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 12).mod(2).floor;

	Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 2 * m.rrateMassThreshold.reciprocal).reciprocal);
	Pdef(m.ptn).set(\amp, 0.34 + (m.accelMassFiltered * 0.03));
	Pdef(m.ptn).set(\octave, 5 + oct);
		
};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered.linlin(0,1,50,0) );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = -5;
~plotMax = 5;

~plot = { |d,p|
	// [d.accelEvent.x, d.accelEvent.y, d.accelEvent.z, d.gyroEvent.pitch, d.gyroEvent.roll, d.gyroEvent.yaw];
	// [d.accelEvent.x, d.accelEvent.y, d.accelEvent.z];
	[ m.accelMassFiltered];
	//[(d.accelEvent.x + 0.5) * 0.1, (d.accelEvent.y + 0.35) * 0.1, (d.accelEvent.z - 9.8) * 0.1];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};
