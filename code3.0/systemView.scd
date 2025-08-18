
var cpu = UserView()
    .minWidth_(60)
    .maxHeight_(20)
    .animate_(true)
    .drawFunc_({|uv|
    ("CPU: "++s.peakCPU.asStringPrec(2)++"%").drawAtPoint(24@8, Font.default, Color.yellow);
});

var nodeTree = UserView()
    .minWidth_(200)
    .maxHeight_(800);


var networkReset = Button()
        .maxWidth_(80)
        .maxHeight_(40)
        .states_([["Network",Color.gray(0.5),Color.gray(0.25)]])
        .action_({
                ("sudo systemctl restart NetworkManager").unixCmdGetStdOut();
        });


var systemView = {
	View().layout_(VLayout(
        cpu,
        networkReset,
        nodeTree
))
};

s.plotTreeView(0.5, nodeTree);
ServerMeterView.new(s, nodeTree, 400@0, 2, 2);
// ServerMeterView.new(s, nodeTree);

systemView;