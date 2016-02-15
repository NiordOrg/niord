The turf.js buffer operation only works around equator, since the distance passed along to the operation
appears to be converted to degrees.

The bug has been semi-fixed in the turf_patched.js file based on the suggestion listed in:
https://github.com/Turfjs/turf/issues/283

Replace the file when a proper fix is rolled out.



