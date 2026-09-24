This repository contains java tooling for working with snowblind engine games (Baldurs Gate: Dark Alliance etc).

There isn't any end user program to run, you need to know a bit of java and run the tools from an IDE.
I use Jetbrains IDEA. If you open the directory in IDEA it will read the maven POM file and set up the project for you.

### Game Files

You need to own the PS2 game. I have the PAL versions but the NTSC versions should work too.

You need to copy the contents of the DVD into

`/emu/` on windows or linux (generally that would be `c:\emu\` on windows) and `~/ps2_games` on the Mac.

Under this directory create game specific dirs:
`DARK_ALLIANCE`, `CHAMPIONS_OF_NORRATH`, `JUSTICE_LEAGUE_HEROES` or `RTA`.

Look at the file `net.ijbrown.jbdga.loaders.Config` for details.

### Extraction

Run `net.ijbrown.jbgda.demos.ExtractFiles` from the IDE to unpack the GOB and LMP files and
to process the files contained in them. You may need to uncomment some lines in the main method.
This will create a directory called DATA_extracted alongside the DATA directory.
Say there is a file called XXX.LMP in the DATA directory then a directory called `XXX_LMP` will be
created in the DATA_extracted directory and populated with the contents of the LMP file.
The same applies to GOB files (which are collections of LMP files)

TEX files are converted to PNG files and VIF files are converted to GLTF files (which reference a corresponding PNG file).
I use https://sandbox.babylonjs.com/ to test the GLTF files. You need to drop the PNG file onto the browser window first
and then the GLTF file or it will complain about a missing texture.

Note that VIF files often contain a number of meshes and the game may not display all of them. You can select the
individual visibility in the babylon viewer to see the differences. One example here is the chest_large.vif which
has sub-meshes for both the interior exploding powder keg and also various amounts of gold coins. During gameplay the
engine will select one interior to display.

### Running the UI from a release

There is a simple UI to help people not familiar with using a Java IDE.
The first thing you need is Java 25 installed.
Then, download the jar file from github releases and double click it to run.
If you want to see the console output (can be useful), from a terminal prompt
run the jar with `java --jar jbgda.jar`

Once in the UI, select the game you're interested in. The path where the data is expected will be shown in the UI.
Make sure the data is there including the elf file (the SLES / SLUS one) because some data is extracted from that.

If it is the first time, check the extract Lmp checkbox. If the lmps are already extracted, you can uncheck this.
If you now click the extract button, everything will be extracted and converted. The output will be written to a
DATA_EXTRACTED directory next to the DATA directory. It can take a long time - if you are running from the terminal
you can see it writing logs.
If you are only interested in a subset of files, then you can enter a substring in the text field of the UI to
restrict what is processed. So, say you are only interested in the arenabeast vif file, typing arenabeast or
even something like arena will be much faster.
Generally you only do this if you're changing the code - for a normal non-dev user you would only run this tool once.

### Credits

The LWJGL Vulkan code is from https://github.com/lwjglgamedev/vulkanbook

