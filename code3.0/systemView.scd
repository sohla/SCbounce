var bh = 80;

var cpu = UserView()
    .minWidth_(60)
    .maxHeight_(20)
    .animate_(true)
    .drawFunc_({|uv|
    ("CPU: "++s.peakCPU.asStringPrec(1)++"%").drawAtPoint(16@4, Font.default, Color.yellow);
    ("UGens: "++s.numUGens).drawAtPoint(100@4, Font.default, Color.yellow);
    ("Groups: "++s.numGroups).drawAtPoint(200@4, Font.default, Color.yellow);
    ("Synths: "++s.numSynths).drawAtPoint(300@4, Font.default, Color.yellow);
});

var meterView = View()
	.minWidth_(140);

var nodeTree = View()
	.background_(Color.black(0.25))
	.minWidth_(600)
	.maxHeight_(800);

var restartButton = Button()
		.maxWidth_(140)
		.maxHeight_(bh)
		.states_([["QUIT",Color.gray(0.5),Color.gray(0.25)]])
		.action_({
				s.quit({
					0.exit;
				});
		});

var oscThruButton = Button()
		.maxWidth_(140)
		.maxHeight_(bh)
		.states_([
			["OSC Thru",Color.gray(0.5),Color.gray(0.25)],
			["OSC Thru",Color.white(),Color.green(0.25)]
			])
		.action_({|b|
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/oscThru", b.value.asBoolean);
		});


var networkReset = Button()
	.maxWidth_(140)
	.maxHeight_(bh)
	.states_([["Network",Color.gray(0.5),Color.gray(0.25)]])
	.action_({
			("sudo systemctl restart NetworkManager").unixCmdGetStdOut();
	});

var freeAllNodes = Button()
	.maxWidth_(140)
	.maxHeight_(bh)
	.states_([["Free All Nodes",Color.gray(0.5),Color.gray(0.25)]])
	.action_({
			s.freeAll;
	});


var systemView = {
	View().layout_(VLayout(
		cpu,
		GridLayout.columns(
			[
				networkReset,
				freeAllNodes,
				oscThruButton,
				restartButton,
				meterView,

			],[
				[nodeTree, rows:4],
			]
		)
	))
};

s.plotTreeView(0.5, nodeTree);

ServerMeterView.new(s, meterView, 0@0, 2, 2);
systemView;