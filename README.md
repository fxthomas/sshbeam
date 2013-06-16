# Building

Use the latest SBT-Android snapshot (0.7-SNAPSHOT from [my
branch](https://github.com/fxthomas/android-plugin/tree/rewrite-cleanup) at the
moment), and run `apk` to package the app.

# Using

Use the "Share" button in any app while selecting a file (this can be, for
instance, a file manager, your "Downloads" Android app,...), and select "SSH
Beam".

Then, fill in the fields, and hit "Send" to send the file to your SSH server.

At the moment, I'm only checking if the file exists on the server (in which
case I don't overwrite), but that's it. Use at your own risk.

# Copyright

This was made by me, François-Xavier Thomas, but you can pretty much use the
source and the app however you want. This is therefore licensed under the
[WTFPL](http://www.wtfpl.net/about/).

Just be nice and credit me, send me an email, chocolates or gifts of any kind
if you ever decide to use the source for your own purposes.
