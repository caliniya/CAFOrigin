package caliniya.caf.platform;

import arc.files.*;
import caliniya.caf.lagrange.*;

/** Ready platform services. Loading and binding happen before these reach core. */
public interface Platform{
    /** The platform's writable application data directory. */
    Fi dataDirectory();

    /** The mapped native methods, not a library loader. This must never return null. */
    LagrangeApi lagrange();
}
