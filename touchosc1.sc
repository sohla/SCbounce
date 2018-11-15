(
var window;
var device = SLIPDecoder.new("/dev/cu.usbmodem3666050", 115200, 8);
var deviceView, controlView;
var dataSize = 8;
var deviceData = Array.fill(dataSize,0);
var offsets = Array.fill(dataSize,0);
var reset;

device.actions = Array.fill(8,{|index| {|i,input| deviceData[index] = input }});
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
				var step = -2pi / dataSize;
		        Pen.addWedge(w@h, deviceData[i]-  offsets[i], step * i, 2pi/dataSize);
				Pen.fillColor = Color.hsv(i/dataSize,1,1,1);
		        Pen.fill;

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

	device.stop;
	device.close;
	Buffer.freeAll;
	s.freeAll;

});
CmdPeriod.doOnce({window.close});

{reset.()}.defer(0.1);

)
