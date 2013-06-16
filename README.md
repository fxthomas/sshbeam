SSH Beam is a simple Android app with which you can "Share" things to an SSH server,
via the SFTP protocol (you know, the one used by `scp`).

  * Find something to share
  * Click **Share**
  * Select **SSH BEAM**
  * Enter the required info (server, user name,...)
  * Click **Send**
  * Wait until done

At the moment, I'm only checking if the file exists on the server (in which
case I don't overwrite), but that's it. Use at your own risk.

# Use cases

I just built this to scratch an itch, but you can use it for a whole lot of things :

  * Setup a cron job that publishes posts from a specific directory on your
    server, and then publish posts with SSH Beam from your Android device.
  * Send torrents to a directory watched by [rTorrent](http://libtorrent.rakshasa.no/)
  * ...and much more!

# Building

Use the latest SBT-Android snapshot (0.7-SNAPSHOT from [my
branch](https://github.com/fxthomas/android-plugin/tree/rewrite-cleanup) at the
moment), and run `apk` to package the app.

# Copyright

This was made by me, François-Xavier Thomas, but you can pretty much use the
source and the app however you want. This is therefore licensed under the
[WTFPL](http://www.wtfpl.net/about/).

Just be nice and credit me, send me an email, chocolates or gifts of any kind
if you ever decide to use the source for your own purposes.

Icon by [Dutch Icon](http://dutchicon.com/) (via [Smashing
Magazine](http://www.smashingmagazine.com/2012/11/11/dutch-icon-set-smashing-edition/)),
since I don't really have the time to make a cool icon. This one doesn't seem
too bad and is under the CC-BY-SA license. Thanks a lot guys!
