+ OSXPlatform {

	startupFiles {
		^[];
	}

	startup {

		helpDir = this.systemAppSupportDir++"/Help";

		// Server setup
		Server.program = "./MacOS/scsynth -U plugins -D 0";

		// Score setup
		Score.program = Server.program;

		// load user startup file
		//this.loadStartupFiles;
	}

}
