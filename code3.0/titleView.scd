var addTitleView = { |d|
	var fs = 70 - (d.name.size * 3.5);
	var button = Button()
		.minWidth_(100)
		.minHeight_(100)
		.font_(Font(size:fs, bold:true))
		.states_([
			[d.name, Color.black, d.color.alpha_(0.8)],
			[d.name, Color.black.alpha_(0.5), Color.white.alpha_(0.1)],
		])
		.action_({|but|
			if(but.value == 0, {
				NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/reLoadPersonality", d.port, d.personality);
			},{
				NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/unLoadPersonality", d.port, d.personality);
		});
		});

	OSCFunc({ |msg, time, addr, recvPort|
		if(msg[1].asInteger == d.port, {
			var fs = 70 - (msg[2].asString.size * 3.0);
			{
				button.font = Font(size:fs, bold:true);
				button.states = [
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

