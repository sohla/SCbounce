(
var window;
var midiOut, midiChannel = 0, midiController = 1;
var controllerView, keyboardView;
var num  = 12;
var keyViews;
var keys = [81, 87, 69, 82, 84, 89, 85, 73, 79, 80, 91, 93, 92];
var octave = 6, root = 0;
var onKey;
var controllerSpec = \midi.asSpec;
var controlSlider;

//------------------------------------------------------------
QtGUI.palette = QPalette.dark;

MIDIClient.init;
MIDIClient.destinations;

//------------------------------------------------------------

midiOut = MIDIOut.newByName("IAC Driver", "Bus 1", dieIfNotFound: true);
//midiOut = MIDIOut.newByName("Teensy MIDI", "Teensy MIDI", dieIfNotFound: true);
midiOut.latency_(0.1);
//------------------------------------------------------------
//------------------------------------------------------------
//------------------------------------------------------------
//------------------------------------------------------------
keyViews = {|i|
	UserView()
		.background_(Color.hsv(i/num,0.8,0.8,0.2))
		.mouseDownAction_({
			onKey.(keys[i],true);
		})
		.mouseUpAction_({
			onKey.(keys[i],false);
		})
		.mouseMoveAction_({|v,x,y|
			var val = x.half.round.abs;
			val = 127.clip2(val);
			(val/127).postln;
			controlSlider.valueAction_(val/127);
		});
}!num;
//------------------------------------------------------------
keyboardView = {


	VLayout(
		HLayout(
			StaticText()
				.string_("Octave")
				.maxWidth_(60),
			PopUpMenu()
				.items_((0..10))
				.action_({|o|
					octave = o.value;
				}).valueAction_(octave),
			StaticText()
				.string_("Root")
				.maxWidth_(60),
			PopUpMenu()
				.items_((0..8))
				.action_({|o|
					root = o.value;
				}).valueAction_(root)
		),
		HLayout(
			*keyViews.()
		)
	);
};
//------------------------------------------------------------
controllerView = {
	var st;
	HLayout(
		StaticText()
			.string_("Channel")
			.maxWidth_(60),
		PopUpMenu()
			.items_((1..16))
			.action_({|o|
				midiChannel = o.value;
			}).valueAction_(midiChannel),
		StaticText()
			.string_("Controller")
			.maxWidth_(60),
		PopUpMenu()
			.items_((0..127))
			.action_({|o|
				midiController = o.value;
			}).valueAction_(midiController),
		st = StaticText()
			.string_("-")
			.minWidth_(30)
			.maxWidth_(30),
		controlSlider = Slider()
			.orientation_(\horizontal)
			.maxHeight_(30)
			.action_({|o|
				var val = controllerSpec.step_(1).map(o.value);
				st.string_(val);
				midiOut.control(midiChannel, midiController, val);
			}),
	);
};

//------------------------------------------------------------

onKey = {|key, isOn|

	var note = 0;
	var index = keys.indexOf(key);

	if( keys.includes(key),{
		note = (octave * 12) + root + index;

		if(isOn == true,{
			midiOut.noteOn(midiChannel, note, 100);
			keyViews[index].background = keyViews[index].background.alpha = 1.0;
		},{
			midiOut.noteOff(midiChannel, note, 100);
			keyViews[index].background = keyViews[index].background.alpha = 0.2;
		});
	});
};

//------------------------------------------------------------
//------------------------------------------------------------
//------------------------------------------------------------

//------------------------------------------------------------
window = Window("")
	.bounds_(Rect(
		0,0,
		Window.screenBounds.width/2,
		135)
		.center_(Window.availableBounds.center)
	)
	.alwaysOnTop_(true)
	.front;

window.layout = VLayout(
	controllerView.(),
	keyboardView.()
);

//------------------------------------------------------------
window.view.keyDownAction_({|view,char,mods,uni,code,key|
	onKey.(key, true);
});

window.view.keyUpAction_({|view,char,mods,uni,code,key|
	onKey.(key, false);
});
//------------------------------------------------------------
window.onClose = ({

	Buffer.freeAll;
	s.freeAll;

});
CmdPeriod.doOnce({window.close});


)
