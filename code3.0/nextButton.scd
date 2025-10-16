
var addNextButton = { |d|
	var button = Button()
		.minHeight_(100)
		.font_(Font(size:64))
		.states_([
			["→",Color.black,Color.white.alpha_(0.75)],
		])
		.action_({|but|
			d.personality = d.personality + 1;
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", d.port, d.personality);
		});
		button
};

addNextButton