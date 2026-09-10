// The laptop. Values carried from the AirKitDesktop branch.
//
// MU00157721X is an institution-managed asset name and may change on
// reimage; if it does, the \platform fallback still picks this file while
// it is the only macOS one.

(
	label: "desktop",
	hostnames: ["MU00157721X"],
	platform: \osx,

	outAddr: ["192.168.70.211", 5005],
	numDevices: 5,

	list: "list_BtB.sc",

	secs: 0.01,
	latency: 0.03,

	border: true,
	fullScreen: false
)
