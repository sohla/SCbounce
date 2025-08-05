var addTitleView = { |d|
	var button = Button()
		.minWidth_(100)
		.minHeight_(120)
		.font_(Font(size:48))
		.states_([
			[d.name,d.color,Color.white.alpha_(0.8)],
			[d.name,d.color,Color.white.alpha_(0.1)],
		])
		.action_({|but|
			d.enabled = but.value;
		});

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			{button.states = [
				[msg[2].asString, d.color, Color.white.alpha_(0.8)],
				[msg[2].asString,d.color,Color.white.alpha_(0.1)],
			];
			button.valueAction = d.enabled;
			}.defer;
		});
	},'/airkit/personalityName', NetAddr("127.0.0.1", NetAddr.langPort));
	
	button
};
addTitleView

