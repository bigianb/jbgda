This repository contains java tooling for working with snowblind engine games (Baldurs Gate: Dark Alliance etc).

There isn't any end user program to run, you need to know a bit of java and run the tools from an IDE.
I use Jetbrains IDEA. If you open the directory in IDEA it will read the maven POM file and set up the project for you.

### Game Files

You need to own the PS2 game. I have the PAL versions but the NTSC versions should work too.

You need to copy the contents of the DVD into

`/emu/bgda` on windows or linux (generally that would be `c:\emu\bgda\` on windows) and `~/ps2_games` on the Mac.

Under this directory create game specific dirs:
`DARK_ALLIANCE`, `CHAMPIONS_OF_NORRATH`, `JUSTICE_LEAGUE_HEROES`.

Look at the file `net.ijbrown.jbdga.loaders.Config` for details.

### Extraction

Run `net.ijbrown.jbgda.demos.ExtractFiles` from the IDE to unpack the GOB and LMP files and
to process the files contained in them.
This will create a directory called DATA_extracted alongside the DATA directory.
Say there is a file called XXX.LMP in the DATA directory then a directory called `XXX_LMP` will be
created in the DATA_extracted directory and populated with the contents of the LMP file.
The same applies to GOB files (which are collections of LMP files)

