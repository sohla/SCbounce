
var addNextButton = { |d|
	var button = Button()
		.minWidth_(120)	
		.maxWidth_(120)	
		.minHeight_(100)
		.font_(Font(size:64))
		.states_([
			["+",d.color.darken(0.5),Color.white.alpha_(0.75)],
		])
		.action_({|but|
			d.personality = d.personality + 1;
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", d.port, d.personality);
		});
		button
};

addNextButton