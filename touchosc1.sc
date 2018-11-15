(
var window;
var device = SLIPDecoder.new("/dev/cu.usbmodem3666050", 115200, 8);
var deviceView, controlView;
var dataSize = 8;
var deviceData = Array.fill(dataSize,0);
var offsets = Array.fill(dataSize,0);
var reset;
var isOn = false;
var rout;
var sumFiltered = 0;
var midiOut;
var midiChannel = 4;

var creatRout = {
	if(rout.isNil) { 
		rout = Routine{ 
			loop{ 
				f.(); 
				0.01.yield;
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
	var sum = (deviceData.abs.mean - offsets.abs.mean).abs.sqrt;

	if( sum.isNaN == false, {
		sumFiltered =  filter.(sum, sumFiltered, 0.2);
	});

	midiOut.control(midiChannel, 0, (sum*10).asInteger );

	gate.(sumFiltered,1.7,{

		if(isOn!=true,{
			"NoteON".postln;
			midiOut.noteOn(midiChannel, 10, 100);
			isOn = true;
		});
	},{
		if(isOn!=false,{
			"NoteOFF".postln;
			midiOut.noteOff(midiChannel, 10, 100);
			isOn = false;
		});

	});
};

MIDIClient.init;
midiOut = MIDIOut.new(0).latency_(0.01);

device.actions = Array.fill(8,{|index| {|i,input| deviceData[index] = filter.(input, deviceData[index],0.08)  }});
device.start;

reset = {
	dataSize.do{|i| offsets[i] = deviceData[i]};
};

controlView = {

	VLayout( 
		Button()
			.maxWidth_(100)
			.states_([["reset"]])
			.action_({
				reset.();
			})
	)
};

deviceView = {

	UserView()
		.background_(Color.grey)
		.animate_(true)
		.drawFunc={|v|

			var h = v.bounds.height.half;
			var w = v.bounds.width.half;

			dataSize.do{ |i|
		
				var val = deviceData[i] -  offsets[i];
				var sum = (deviceData.abs.mean - offsets.abs.mean).abs.sqrt;
				var step = -2pi / dataSize;
		
		        Pen.addWedge(w@h, val, step * i, 2pi/dataSize);
				Pen.fillColor = Color.hsv(i/dataSize,1,1,1);
		        Pen.fill;

				Pen.addArc(w@h, sum * 10.0, 0, 2pi);
				Pen.stroke;
			};

		};

};

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
