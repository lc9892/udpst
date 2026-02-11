# UDP Speed Test for Android

The **UDPST** application replicates core client components from the formal Open
Broadband - UDP Speed Test (**OB-UDPST**) Linux software release, sponsored by
the [**Broadband Forum**](https://www.broadband-forum.org/). The OB-UDPST
[project page](https://broadband-forum.atlassian.net/wiki/spaces/BBF/pages/46640109/Open+Broadband-UDP+Speed+Test+OB-UDPST) and public
[software mirror](https://github.com/BroadbandForum/obudpst) are available
for additional details.

## Software Architecture

This application is built using Android Studio.

*Note: The client protocol utilized with this project is controlled via
the **CLIENT_VER_IS_LATEST** constant, which selects for either the most recent
9.0.0 software version (protocol version 20) or the legacy 8.2.0 software version
(protocol version 11). To provide the widest range of compatibility, given that
a 9.0.0 server is backward compatible with 8.2.0 clients, the default for
this constant is **false**.*

IMPORTANT: As of the 9.0.0 release of OB-UDPST, the default control port was
changed from 25000 to 24601. When testing with a 9.0.0 server, the client
can either use the new control port (24601) or the server can be run with the
legacy control port (via `-p 25000`).
