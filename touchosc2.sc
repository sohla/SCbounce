(
var window;
var device = SLIPDecoder.new("/dev/cu.usbmodem3666050", 115200, 8);
var deviceView, controlView;
var dataSize = 8;
var deviceData = Array.fill(dataSize,0);
var offsets = Array.fill(dataSize,0);
var reset;
var isOn = Array.fill(dataSize,false);
var rout;
var sumFiltered = 0;
var midiOut;
var midiChannel = 0;
var midiChannelSpec = [0,9,'linear',1].asSpec;
var seq = [0,3];
var root = seq[0];

var creatRout = {
	if(rout.isNil) { 
		rout = Routine{ 
			loop{ 
				f.(); 
				0.03.yield;
			}; 
		}.play; 
	};
};

var killRout = {
	rout.stop; rout = nil;
};

var filter = {|input,history,friction = 0.5|
	(friction * input + ((1 - friction) * history))
};

var gate = {|input, threshold, fa, fb|
	if(input > threshold, {fa.()},{fb.()});
};

var f = {

	var step = -2pi / dataSize;
//	var notes = [0-24,4,7-12,10,12,14,17,19];
	var notes = [-24,-12,0,7,12,19,24,26];

				if( isOn.asInt.mean.floor.asBoolean,{

				},{
							seq = seq.rotate(-1);
							root = seq[0];

				});

	dataSize.do{|i|

		var sum = deviceData[i]-offsets[i];
		var v = (sum.sqrt * 5).asInteger.clip(0,127);

		// [sum].postln;

		if( sum.isNaN == false, {
			sumFiltered =  filter.(sum, sumFiltered, 0.02);
		});
		midiOut.control(i, 0, v );

		gate.(sum,5.0,{

			if(isOn[i]!=true,{
				"NoteON".postln;
				midiOut.noteOn(i, 72 + notes[i] + root, 100);
				isOn[i] = true;
			});
		},{
			if(isOn[i]!=false,{
				"NoteOFF".postln;
				midiOut.noteOff(i, 72 + notes[i] + root, 100);
				midiOut.allNotesOff(i);
				isOn[i] = false;

			});

		});

	};

};

MIDIClient.init;
midiOut = MIDIOut.new(0).latency_(0.01);

device.actions = Array.fill(8,{|index| {|i,input| deviceData[index] = filter.(input, deviceData[index],0.08)  }});
device.start;

reset = {
	dataSize.do{|i| offsets[i] = deviceData[i]};
};

controlView = {

	HLayout( 
		Button()
			.maxWidth_(100)
			.maxHeight_(40)
			.states_([["reset"]])
			.action_({
				isOn = Array.fill(dataSize,false);
				reset.();
				dataSize.do{|i|midiOut.allNotesOff(i)};
			}),
		Knob()
			.maxWidth_(30)
			.maxHeight_(40)
			.step_(1)
			.action_({|o|
				midiChannel = midiChannelSpec.map(o.value);
			})

	)
};

deviceView = {

	UserView()
		.background_(Color.black)
		.animate_(true)
		.drawFunc={|v|

			var h = v.bounds.height.half;
			var w = v.bounds.width.half;

			dataSize.do{ |i|
		
				var val = deviceData[i] -  offsets[i];
				var sum = (deviceData.abs.mean - offsets.abs.mean).abs.sqrt;
				var step = -2pi / dataSize;
		
		        Pen.addWedge(w@h, val, step * i, 2pi/dataSize);
				Pen.fillColor = Color.hsv(i/dataSize,0.5,1,1);
		        Pen.fill;

				Pen.addArc(w@h, sum * 10.0, 0, 2pi);
				Pen.strokeColor = Color.white;
				Pen.stroke;
			};

		};

};
QtGUI.palette = QPalette.dark; 

window = Window("")
	.bounds_(Rect(
		0,0,
		Window.screenBounds.width/2,
		Window.screenBounds.height/2)
		.center_(Window.availableBounds.center)
	)
	.front;

window.layout = VLayout(deviceView.(), controlView.());

window.onClose = ({

	dataSize.do{|i|midiOut.allNotesOff(i)};
	killRout.();
	device.stop;
	device.close;
	Buffer.freeAll;
	s.freeAll;

});
CmdPeriod.doOnce({window.close});

{
	reset.();
	creatRout.();
}.defer(0.8);

)
a = [false,false]

a.asInt.mean.floor.asBoolean