var bh = 100;
var hosts = "hostname -I".unixCmdGetStdOut.replace($ ," | ");

var getSCLoad = {
	var pidLine, sumLine, raw, norm;
    pidLine = "pgrep -f sclang | tr '\n' ',' | sed 's/,$//'".unixCmdGetStdOut.stripWhiteSpace;
    if (pidLine.notEmpty) {
        sumLine = ("ps -p " ++ pidLine ++ " -o %cpu --no-header | awk '{s+=$1} END{print s}'")
            .unixCmdGetStdOut.stripWhiteSpace;
        raw  = sumLine.asFloat;
        norm = (raw / 4).round(0.1); // 4 cores
        [raw, norm]
    } {
        [0.0, 0.0]  // sclang not found
    }
};

var getSystemLoad = {
    var result = "cat /proc/loadavg".unixCmdGetStdOut;
    var load = result.split($ )[0].asFloat * 100 / 4; // Assuming 4 cores
	load.round(0.1) 
};

var getTemp = {
    var result = "cat /sys/class/thermal/thermal_zone0/temp".unixCmdGetStdOut;
    var temp = result.asInteger / 1000.0; // Convert millidegrees to degrees
    temp
};

var getFan = {
    var result = "cat /sys/class/thermal/cooling_device0/cur_state".unixCmdGetStdOut;
		result.asInteger 
};

var cpuView = UserView()
	.minWidth_(60)
	.maxHeight_(30)
	.frameRate_(10)
	.animate_(true)
	.drawFunc_({|uv|
			("CPU Load: "++getSystemLoad.().asStringPrec(2)++"%").drawAtPoint(10@4, Font.default, Color.yellow);
			("CPU Load: "++getSCLoad.()[1].asStringPrec(2)++"%").drawAtPoint(120@4, Font.default, Color.yellow);
			("Temp: "++getTemp.().asStringPrec(2)++"°C"++".   Fan: "++getFan.()).drawAtPoint(220@4, Font.default, Color.yellow);
});

var serverView = UserView()
    .minWidth_(60)
    .maxHeight_(30)
	.frameRate_(10)
    .animate_(true)
    .drawFunc_({|uv|
			("Server: "++s.peakCPU.asStringPrec(3)++"%").drawAtPoint(10@4, Font.default, Color.yellow);
			("UGens: "++s.numUGens).drawAtPoint(120@4, Font.default, Color.yellow);
			("Groups: "++s.numGroups).drawAtPoint(200@4, Font.default, Color.yellow);
			("Synths: "++s.numSynths).drawAtPoint(280@4, Font.default, Color.yellow);
			("Hosting: "++hosts).drawAtPoint(400@4, Font.default, Color.yellow);
			("Port: "++NetAddr.	localAddr.port).drawAtPoint(1000@4, Font.default, Color.yellow);
});

var meterView = View()
	.minWidth_(150)
	.maxWidth_(150);

var nodeTree = View()
	.background_(Color.black(0.25))
	.minWidth_(700)
	.maxHeight_(800);

var turnOffButton = Button()
		.font_(Font(size:18, bold:true))
		// .maxWidth_(140)
		.maxHeight_(bh)
		.states_([["TURN OFF",Color.black,Color.red(1)]])
		.action_({
				"sudo shutdown now".unixCmdGetStdOut;
		});

var quitButton = Button()
		.font_(Font(size:18, bold:false))
		// .maxWidth_(140)
		.maxHeight_(bh)
		.states_([["QUIT",Color.black,Color.white.alpha_(0.75)]])
		.action_({
				s.quit({
					0.exit;
				});
		});


var oscThruButton = Button()
		.font_(Font(size:18, bold:false))
		// .maxWidth_(140)
		.maxHeight_(bh)
		.states_([
			["OSC Thru",Color.black,Color.gray(0.5)],
			["OSC Thru",Color.white(),Color.green(0.25)]
			])
		.action_({|b|
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/oscThru", b.value.asBoolean);
		});

var networkReset = Button()
	.font_(Font(size:18, bold:false))
	// .maxWidth_(140)
	.maxHeight_(bh)
	.states_([["Network",Color.black,Color.gray(0.5)]])
	.action_({
			("sudo systemctl restart NetworkManager").unixCmdGetStdOut();
	});

var freeAllNodes = Button()
	.font_(Font(size:18, bold:false))
	// .maxWidth_(140)
	.maxHeight_(bh)
	.states_([["Free All Nodes",Color.black,Color.gray(0.5)]])
	.action_({
		s.freeAll;
	});

var freeAllBuffers = Button()
	.font_(Font(size:18, bold:false))
	// .maxWidth_(140)
	.maxHeight_(bh)
	.states_([["Free All buffers",Color.black,Color.gray(0.5)]])
	.action_({
		Buffer.freeAll(s);
	});

var systemView = {
	View().layout_(VLayout(
		HLayout(View().maxWidth_(140), cpuView),
		HLayout(View().maxWidth_(140), serverView),
		HLayout(meterView, nodeTree),
		HLayout(
			View(),
			networkReset,
			oscThruButton,
			freeAllBuffers,
			freeAllNodes,
			quitButton,
			View(),
			turnOffButton,
		)
	))
};

s.plotTreeView(0.5, nodeTree);

ServerMeterView.new(s, meterView, 0@0, 2, 2);
systemView;