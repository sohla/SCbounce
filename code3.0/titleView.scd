var addTitleView = { |d|
	var button = Button()
		.minWidth_(100)
		.minHeight_(100)
		.font_(Font(size:40, bold:true	))
		.states_([
			[d.name, Color.black, d.color.alpha_(0.8)],
			[d.name, Color.black.alpha_(0.5), Color.white.alpha_(0.1)],
		])
		.action_({|but|
			if(but.value == 0, {
				NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/reLoadPersonality", d.port, d.personality);
			},{
				NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/unLoadPersonality", d.port, d.personality);
		});
		});

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			{button.states = [
				[msg[2].asString, Color.black, d.color.alpha_(0.8)],
				[msg[2].asString, Color.black.alpha_(0.5), Color.white.alpha_(0.1)],
			];
			}.defer;
		});
	},'/airkit/personalityName', NetAddr("127.0.0.1", NetAddr.langPort));

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			{ button.valueAction = 1 }.defer;
		});
	},'/airkit/mute');

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			{ button.valueAction = 0 }.defer;
		});
	},'/airkit/unmute');

	button
};
addTitleView

