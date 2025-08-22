var addReloadButton = { |d|
	var button = Button()
		.maxWidth_(120)
		.minHeight_(120)
		.font_(Font(size:24))
		.states_([
			["∫",d.color,Color.white.alpha_(0.1)],
		])
		.action_({|but|
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", d.port, d.personality);
		});
		button
};
addReloadButton