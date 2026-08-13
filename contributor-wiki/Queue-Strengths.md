For queues used in server logic:

WEAK - cleared if the player interacts with anything else or moves anywhere else. Also cleared when STRONG scripts are added to the queue.

NORMAL - same as weak but persists in the queue with STRONG scripts. pauses execution if an interface is open.

STRONG - clears any weak scripts in the queue for as long as it is present. closes any open interfaces prior to executing. cannot be cleared by any means other than finishing.

-----------All strengths above this line pause if the entity is affected by `delayEntity`-----------

SOFT - does not clear any script. does not close interfaces. cannot be cleared by any means other than finishing.