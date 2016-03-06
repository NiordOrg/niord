# niord-josm-seachart

The niord-josm-seachart library is merely a copy of the Niord JSOM seachart plugin
<a href="https://github.com/openstreetmap/josm-plugins">https://github.com/openstreetmap/josm-plugins</a>.

In Niord, the library is used for creating those pesky AtoN icons...

## Modifications

* The library has been refactored to build using maven.
* The following JOSM seachart sub-packages have selectively been merged into niord-josm-seachart: render, s57 and symbols.
* To allow for better integration into Niord, System.exits() and System.err logging has been purged...

## Credits

The main contributor to the seachart JOSM plugin seems to be <a href="https://github.com/malcolmh">Malcolm Herring</a>.

 
