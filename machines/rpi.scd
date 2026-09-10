// The Pi kit. Only this machine ever edits this file.
//
// \hostnames is empty because no Pi hostname has been verified yet - the
// \platform fallback in machine.scd picks this file on any Linux kit while
// it is the only one. Run `hostname` on the Pi and add it here before a
// second Linux machine file exists.

(
	label: "rpi",
	hostnames: [],
	platform: \linux,

	outAddr: ["192.168.50.53", 5005],
	numDevices: 9,

	// list: "list_ITR_Mel.sc",
	// list: "list_SiS_Alessio.sc",
	// list: "list_dev.sc",
	// list: "list_Mel_26.sc",
	// list: "list_NZ.sc",
	// list: "list_ITR_Nic.sc",
	// list: "list_yourDNA25.sc",
	// list: "list_workshop1.sc",
	// list: "list_alon26.sc",
	// list: "list_yourDNA26.sc",
	// list: "list_BtB.sc",
	list: "list_glenroy.sc",

	// a roster per device, in device order, instead of one for all:
	// list: ["list_NZ_Mel.sc", "list_NZ_Mel.sc", "list_NZ_Suz.sc", "list_NZ_Suz.sc", "list_NZ_Suz.sc"],
	// list: ["list_MIM25_James.sc", "list_MIM25_Caz.sc", "list_MIM25_Tim.sc", "list_MIM25_Matt.sc", "list_MIM25_Heather.sc"],
	// list: ["list_ITR_Mel.sc", "list_ITR_Nic.sc", "list_ITR_Brenton.sc", "list_ITR_Alessio.sc", "list_ITR_Mel.sc"],

	secs: 0.01,
	latency: 0.1,

	border: false,
	fullScreen: true
)
