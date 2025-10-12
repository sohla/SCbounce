var bh = 80;
var hosts = "hostname -I".unixCmdGetStdOut;


var updateCPU = {
    var pipe;
    
    // Use a single command that doesn't sleep (faster)
    pipe = Pipe.new("top -bn2 -d0.1 | grep '^%Cpu' | tail -n1 | awk '{print \"Total CPU: \" 100-$8 \"%\"}'", "r");
    
    {
        var result = pipe.getLine;
        pipe.close;
        result.postln;
        // Update GUI here
    }.defer;
};

var getCPUInstant = {
    var result = "cat /proc/loadavg".unixCmdGetStdOut;
    var load = result.split($ )[0].asFloat * 100 / 4; // Assuming 4 cores
    
    {
        ("CPU Load: " ++ load.round(0.1) ++ "%").postln;
        // Update GUI
    }.defer;
};
var getTemp = {
    var result = "cat /sys/class/thermal/thermal_zone0/temp".unixCmdGetStdOut;
    var temp = result.asInteger / 1000.0; // Convert millidegrees to degrees
    temp.postln;
};

var serverView = UserView()
    .minWidth_(60)
    .maxHeight_(20)
		.frameRate_(10)
    .animate_(true)
    .drawFunc_({|uv|
			// fork {
			// 	var str = "mpstat -P ALL 1 1 | awk '/Average:/ && $2 ~ /[0-9]/ {printf \"Core %s: %.1f%% \", $2, 100-$NF}'";
			// 	var tot = "grep '^cpu ' /proc/stat > /tmp/cpu1; sleep 1; grep '^cpu ' /proc/stat > /tmp/cpu2; paste /tmp/cpu1 /tmp/cpu2 | awk '{user1=$2; nice1=$3; sys1=$4; idle1=$5; iowait1=$6; irq1=$7; soft1=$8; user2=$13; nice2=$14; sys2=$15; idle2=$16; iowait2=$17; irq2=$18; soft2=$19; total1=user1+nice1+sys1+idle1+iowait1+irq1+soft1; total2=user2+nice2+sys2+idle2+iowait2+irq2+soft2; total_diff=total2-total1; idle_diff=idle2-idle1; usage=100*(total_diff-idle_diff)/total_diff; printf \"Total CPU: %.1f%%\\n\", usage}'";			
			// 	var perCoreResult, totalResult;
				
			// 	perCoreResult = str.unixCmdGetStdOut;
			// 	totalResult = tot.unixCmdGetStdOut;
				
			// 	{
			// 		perCoreResult.postln;
			// 		totalResult.postln;
			// 		// (perCoreResult).drawAtPoint(750@4, Font.default, Color.yellow);
			// 		// (totalResult).drawAtPoint(1150@4, Font.default, Color.yellow);
			// 	}.defer;
			// };
			// getCPUInstant.();
			// getTemp.();
			("Server: "++s.peakCPU.asStringPrec(3)++"%").drawAtPoint(6@4, Font.default, Color.yellow);
			("Hosting: "++hosts).drawAtPoint(110@4, Font.default, Color.yellow);
			("Port: "++NetAddr.	localAddr.port).drawAtPoint(350@4, Font.default, Color.yellow);
			("UGens: "++s.numUGens).drawAtPoint(450@4, Font.default, Color.yellow);
			("Groups: "++s.numGroups).drawAtPoint(550@4, Font.default, Color.yellow);
			("Synths: "++s.numSynths).drawAtPoint(650@4, Font.default, Color.yellow);
});


var meterView = View()
	.minWidth_(140);

var nodeTree = View()
	.background_(Color.black(0.25))
	.minWidth_(600)
	.maxHeight_(800);

var shutdownButton = Button()
		.maxWidth_(140)
		.maxHeight_(bh)
		.states_([["SHUTDOWN",Color.gray(0.5),Color.gray(0.25)]])
		.action_({
				"sudo shutdown now".unixCmdGetStdOut;
		});

var quitButton = Button()
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
		serverView,
		GridLayout.columns(
			[
				networkReset,
				freeAllNodes,
				oscThruButton,
				quitButton,
				shutdownButton,
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