var m = ~model;
var d = ~device;

var isOn = false;
var bl = [0,-7,-5].stutter(12);
var cr = [0,-2,-7,-3,0,-2,-7,-3,-5].stutter(5);

var rateValue = 0;

var note = 60;
m.midiChannel = 0;
// m.accelMassAmpThreshold = 1.4;
// m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			// \note, Pseq([0],inf),
			\note, Pseq([0,1,2,3,4,5,6,7],inf),
			//\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.2);
	Pdef(m.ptn).play();

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|

	if(state == true,{
		note = d.blob.center.x.linlin(0,350,24,110);
		m.midiOut.noteOn(m.midiChannel, note, 100);
	},{
		m.midiOut.noteOff(m.midiChannel, note, 0);
	});
	// var oc = [12,24];
	// var note, occ;

	// if(state == true,{
	// 	cr = cr.rotate(-1);
		
	// 	occ = oc.choose;
	// 	note = 60  - oc.choose + m.com.root + cr[0];
	// 	m.midiOut.noteOn(m.midiChannel + 3, note, 100);
	// 	{m.midiOut.noteOff(m.midiChannel + 3, note, 0)}.defer(0.8);
		
	// 	note = 60  - 24 - occ + m.com.root + cr[0];
	// 	m.midiOut.noteOn(m.midiChannel + 4, note, 60);
	// 	{m.midiOut.noteOff(m.midiChannel + 4, note, 0)}.defer(0.8);

	// },{
	// });
};

~onMoving = {|state|

	// if(state == true,{
	// 	Pdef(m.ptn).resume();
	// },{
	// 	Pdef(m.ptn).pause();
	// });
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


	// d.sensors.accelEvent.x = d.blob.center.x / 450.0;
	// d.sensors.accelEvent.y = d.blob.center.y / 450.0;

	m.accelMassAmp = (d.blob.center.x / 450.0) + (d.blob.center.y / 450.0);
	// var oct = ((0.2 + m.rrateMassFiltered.cubed) * 12).mod(2).floor;

	// Pdef(m.ptn).set(\root,m.com.root);
	// Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 2 * m.rrateMassThreshold.reciprocal).reciprocal);
	// Pdef(m.ptn).set(\amp, 0.34 + (m.accelMassFiltered * 0.03));
	// Pdef(m.ptn).set(\octave, 5 + oct);

	// d.sensors.rrateMass.postln;	

	rateValue = 0.8 + ((m.rrateMassFiltered.log10) * 0.25);


	Pdef(m.ptn).set(\dur, (0.95 - rateValue));

	if(rateValue > 0.55, {
		Pdef(m.ptn).resume();
	},{
		Pdef(m.ptn).pause();
	});


};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered.linlin(0,1,50,0) );

	if(m.isHit,{
		m.midiOut.control(m.midiChannel, 0, d.blob.center.y.linlin(0,150,127,0) );
		m.midiOut.control(m.midiChannel, 1, d.blob.center.x.linlin(0,200,0,127) );
	});

};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	//[d.sensors.gyroEvent.x,d.sensors.gyroEvent.y,d.sensors.gyroEvent.z];

	[ (rateValue) , m.accelMassFiltered - 5];
	 // [ m.accelMassFiltered];
	//[m.accelMassAmp, d.blob.center.x / 450.0, d.blob.center.y / 450.0];
	// [d.blob.area / 450.0, d.blob.perimeter / 450.0, d.blob.center.x / 450.0, d.blob.center.y / 450.0];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

};


